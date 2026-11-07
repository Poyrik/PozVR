package com.example.gearvr

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

@SuppressLint("MissingPermission")
class GearVRBluetoothManager(private val context: Context) {

    companion object {
        private const val TAG = "GearVR-BLE"

        // Official Samsung Gear VR "Oculus Threemote" GATT UUIDs
        val UUID_GEARVR_SERVICE: UUID = UUID.fromString("4f63756c-7573-2054-6872-65656d6f7465")
        val UUID_NOTIFY_CHAR: UUID = UUID.fromString("c8c51726-81bc-483b-a052-f7a14ea3d281")
        val UUID_WRITE_CHAR: UUID = UUID.fromString("c8c51726-81bc-483b-a052-f7a14ea3d282")
        val UUID_CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // 2-Byte Controller Protocol Commands
        val CMD_SENSOR = byteArrayOf(0x01, 0x00)
        val CMD_KEEP_ALIVE = byteArrayOf(0x04, 0x00)
        val CMD_LPM_DISABLE = byteArrayOf(0x07, 0x00) // Crucial: disables low-power throttling
        val CMD_VR_MODE = byteArrayOf(0x08, 0x00)
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private val _controllerState = MutableStateFlow(GearVRState())
    val controllerState: StateFlow<GearVRState> = _controllerState.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<BleDeviceItem>>(emptyList())
    val scannedDevices: StateFlow<List<BleDeviceItem>> = _scannedDevices.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(bluetoothAdapter?.isEnabled == true)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private var currentGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private val orientationFusion = OrientationFusion()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var keepAliveJob: Job? = null
    private var simulationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private var packetCount = 0
    private var lastFpsCalculationTime = System.currentTimeMillis()
    private var lastPacketTimeNs = 0L

    val isSupported: Boolean get() = bluetoothAdapter != null

    init {
        refreshBluetoothState()
        loadBondedDevices()
    }

    fun refreshBluetoothState() {
        val enabled = bluetoothAdapter?.isEnabled == true
        _isBluetoothEnabled.value = enabled
        if (enabled) {
            loadBondedDevices()
        }
    }

    fun loadBondedDevices() {
        val adapter = bluetoothAdapter ?: return
        try {
            val bonded = adapter.bondedDevices?.map { device ->
                val name = device.name ?: "Unknown Device"
                BleDeviceItem(
                    name = name,
                    address = device.address,
                    isBonded = true,
                    isGearVRController = isGearVRDevice(name)
                )
            } ?: emptyList()

            // Merge with current scanned list, preserving unique addresses
            val currentMap = _scannedDevices.value.associateBy { it.address }.toMutableMap()
            bonded.forEach { currentMap[it.address] = it }
            _scannedDevices.value = currentMap.values.sortedByDescending { it.isGearVRController }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bonded devices", e)
        }
    }

    private fun isGearVRDevice(name: String?): Boolean {
        if (name == null) return false
        val lower = name.lowercase()
        return lower.contains("gear vr") || lower.contains("threemote") || lower.contains("oculus")
    }

    // ==========================================
    // BLE Scanning
    // ==========================================

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = device.name ?: result.scanRecord?.deviceName ?: "Unnamed Device"
            val address = device.address
            val rssi = result.rssi
            val isGearVR = isGearVRDevice(name) ||
                    (result.scanRecord?.serviceUuids?.any { it == ParcelUuid(UUID_GEARVR_SERVICE) } == true)

            val item = BleDeviceItem(
                name = name,
                address = address,
                rssi = rssi,
                isBonded = device.bondState == BluetoothDevice.BOND_BONDED,
                isGearVRController = isGearVR
            )

            val current = _scannedDevices.value.toMutableList()
            val existingIndex = current.indexOfFirst { it.address == address }
            if (existingIndex >= 0) {
                current[existingIndex] = item
            } else {
                current.add(item)
            }
            _scannedDevices.value = current.sortedByDescending { it.isGearVRController }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with code $errorCode")
            _controllerState.value = _controllerState.value.copy(
                connectionState = GearVRConnectionState.ERROR,
                errorMessage = "Scan failed (error $errorCode)"
            )
        }
    }

    fun startScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: run {
            _controllerState.value = _controllerState.value.copy(
                errorMessage = "Bluetooth scanner not available"
            )
            return
        }

        stopSimulation()
        _controllerState.value = _controllerState.value.copy(
            connectionState = GearVRConnectionState.SCANNING,
            errorMessage = null
        )

        try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            // Filter for Oculus Threemote service or general scan
            val filters = listOf(
                ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID_GEARVR_SERVICE)).build(),
                ScanFilter.Builder().build()
            )

            scanner.startScan(filters, settings, scanCallback)

            // Auto-stop scan after 12 seconds
            mainHandler.postDelayed({
                stopScan()
            }, 12000L)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BLE scan", e)
        }
    }

    fun stopScan() {
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            if (_controllerState.value.connectionState == GearVRConnectionState.SCANNING) {
                _controllerState.value = _controllerState.value.copy(
                    connectionState = GearVRConnectionState.DISCONNECTED
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan", e)
        }
    }

    // ==========================================
    // Controller Connection & GATT
    // ==========================================

    fun connect(address: String) {
        stopScan()
        stopSimulation()
        disconnect()

        val adapter = bluetoothAdapter ?: return
        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: Exception) {
            _controllerState.value = _controllerState.value.copy(
                connectionState = GearVRConnectionState.ERROR,
                errorMessage = "Invalid device address: $address"
            )
            return
        }

        _controllerState.value = _controllerState.value.copy(
            connectionState = GearVRConnectionState.CONNECTING,
            deviceName = device.name ?: "Gear VR Controller",
            deviceAddress = address,
            errorMessage = null
        )

        try {
            currentGatt = device.connectGatt(
                context,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
        } catch (e: Exception) {
            Log.e(TAG, "connectGatt failed", e)
            _controllerState.value = _controllerState.value.copy(
                connectionState = GearVRConnectionState.ERROR,
                errorMessage = "Connection error: ${e.message}"
            )
        }
    }

    fun disconnect() {
        keepAliveJob?.cancel()
        keepAliveJob = null
        try {
            currentGatt?.disconnect()
            currentGatt?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting GATT", e)
        }
        currentGatt = null
        writeCharacteristic = null
        orientationFusion.reset()

        if (!_controllerState.value.isSimulated) {
            _controllerState.value = GearVRState(
                connectionState = GearVRConnectionState.DISCONNECTED
            )
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "GATT connected, discovering services...")
                _controllerState.value = _controllerState.value.copy(
                    connectionState = GearVRConnectionState.CONNECTED
                )
                // Request higher connection priority for 60Hz-120Hz low latency
                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                mainHandler.postDelayed({
                    gatt.discoverServices()
                }, 200L)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "GATT disconnected (status $status)")
                disconnect()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed with status $status")
                return
            }

            val service = gatt.getService(UUID_GEARVR_SERVICE)
            if (service == null) {
                Log.w(TAG, "Gear VR service $UUID_GEARVR_SERVICE not found!")
                _controllerState.value = _controllerState.value.copy(
                    connectionState = GearVRConnectionState.ERROR,
                    errorMessage = "Oculus Threemote service not found on this device"
                )
                return
            }

            writeCharacteristic = service.getCharacteristic(UUID_WRITE_CHAR)
            val notifyChar = service.getCharacteristic(UUID_NOTIFY_CHAR)

            if (notifyChar != null) {
                // Enable local notification
                gatt.setCharacteristicNotification(notifyChar, true)

                // Write CCCD descriptor to subscribe to notifications on the device
                val descriptor = notifyChar.getDescriptor(UUID_CCCD)
                if (descriptor != null) {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }
            }

            // Sequence controller start commands
            mainHandler.postDelayed({
                // Step 1: Disable Low Power Mode (prevents 100ms burst throttles)
                sendCommand(CMD_LPM_DISABLE)

                // Step 2: Start sensor streaming mode
                mainHandler.postDelayed({
                    sendCommand(CMD_SENSOR)
                    _controllerState.value = _controllerState.value.copy(
                        connectionState = GearVRConnectionState.STREAMING
                    )
                    startKeepAlive()
                }, 100L)
            }, 300L)
        }

        @Deprecated("Deprecated in Java for API 33, handled for backward compatibility")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == UUID_NOTIFY_CHAR) {
                val data = characteristic.value ?: return
                parseGearVRPacket(data)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == UUID_NOTIFY_CHAR) {
                parseGearVRPacket(value)
            }
        }
    }

    private fun sendCommand(cmd: ByteArray) {
        val char = writeCharacteristic ?: return
        val gatt = currentGatt ?: return
        try {
            char.value = cmd
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            gatt.writeCharacteristic(char)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send command to controller", e)
        }
    }

    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            while (isActive && currentGatt != null) {
                delay(1000L)
                sendCommand(CMD_KEEP_ALIVE)
            }
        }
    }

    // ==========================================
    // 60-Byte Packet Decoder & IMU Fusion
    // ==========================================

    private fun parseGearVRPacket(data: ByteArray) {
        if (data.size < 60) return

        val nowNs = System.nanoTime()
        val dt = if (lastPacketTimeNs == 0L) 0.016f else ((nowNs - lastPacketTimeNs) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
        lastPacketTimeNs = nowNs

        // Touchpad coordinates (0..315 mm scale mapped to 10-bit integer)
        val b54 = data[54].toInt() and 0xFF
        val b55 = data[55].toInt() and 0xFF
        val b56 = data[56].toInt() and 0xFF

        val touchX = (((b54 and 0x0F) shl 6) or ((b55 and 0xFC) shr 2)) and 0x3FF
        val touchY = (((b55 and 0x03) shl 8) or b56) and 0x3FF
        val isTouching = (touchX != 0 || touchY != 0)

        // Hardware Buttons in byte 58
        val buttons = data[58].toInt() and 0xFF
        val trigger = (buttons and 0x01) != 0
        val home = (buttons and 0x02) != 0
        val back = (buttons and 0x04) != 0
        val touchpadClick = (buttons and 0x08) != 0
        val volUp = (buttons and 0x10) != 0
        val volDown = (buttons and 0x20) != 0

        val temperature = data[57].toInt() and 0xFF

        // 3 Accelerometer and Gyroscope samples per packet. Average them for maximum stability
        var sumAx = 0f
        var sumAy = 0f
        var sumAz = 0f
        var sumGx = 0f
        var sumGy = 0f
        var sumGz = 0f

        for (k in 0..2) {
            val base = 16 * k
            val rawAx = readInt16LE(data, 4 + base)
            val rawAy = readInt16LE(data, 6 + base)
            val rawAz = readInt16LE(data, 8 + base)
            val rawGx = readInt16LE(data, 10 + base)
            val rawGy = readInt16LE(data, 12 + base)
            val rawGz = readInt16LE(data, 14 + base)

            // Convert to SI units (m/s^2 for accel, rad/s for gyro)
            sumAx += (rawAx * 10000.0f * 9.80665f / 2048.0f) * 0.00001f
            sumAy += (rawAy * 10000.0f * 9.80665f / 2048.0f) * 0.00001f
            sumAz += (rawAz * 10000.0f * 9.80665f / 2048.0f) * 0.00001f

            sumGx += (rawGx * 10000.0f * 0.017453292f / 14.285f) * 0.0001f
            sumGy += (rawGy * 10000.0f * 0.017453292f / 14.285f) * 0.0001f
            sumGz += (rawGz * 10000.0f * 0.017453292f / 14.285f) * 0.0001f
        }

        val ax = sumAx / 3f
        val ay = sumAy / 3f
        val az = sumAz / 3f
        val gx = sumGx / 3f
        val gy = sumGy / 3f
        val gz = sumGz / 3f

        // Feed to orientation fusion
        orientationFusion.update(ax, ay, az, gx, gy, gz, dt)

        // FPS calculation
        packetCount++
        val now = System.currentTimeMillis()
        var currentPps = _controllerState.value.packetsPerSec
        if (now - lastFpsCalculationTime >= 1000L) {
            currentPps = packetCount
            packetCount = 0
            lastFpsCalculationTime = now
        }

        _controllerState.value = _controllerState.value.copy(
            quatW = orientationFusion.qW,
            quatX = orientationFusion.qX,
            quatY = orientationFusion.qY,
            quatZ = orientationFusion.qZ,
            pitchDeg = orientationFusion.pitch,
            yawDeg = orientationFusion.yaw,
            rollDeg = orientationFusion.roll,
            triggerPressed = trigger,
            touchpadClicked = touchpadClick,
            backPressed = back,
            homePressed = home,
            volumeUpPressed = volUp,
            volumeDownPressed = volDown,
            touchX = touchX.toFloat(),
            touchY = touchY.toFloat(),
            isTouching = isTouching,
            accelX = ax,
            accelY = ay,
            accelZ = az,
            gyroX = gx,
            gyroY = gy,
            gyroZ = gz,
            temperatureC = temperature,
            packetsPerSec = currentPps,
            lastPacketTimestamp = now,
            isSimulated = false
        )
    }

    private fun readInt16LE(b: ByteArray, offset: Int): Short {
        val low = b[offset].toInt() and 0xFF
        val high = b[offset + 1].toInt()
        return ((high shl 8) or low).toShort()
    }

    fun reCenter() {
        orientationFusion.reCenter()
        _controllerState.value = _controllerState.value.copy(
            pitchDeg = orientationFusion.pitch,
            yawDeg = orientationFusion.yaw,
            rollDeg = orientationFusion.roll,
            quatW = orientationFusion.qW,
            quatX = orientationFusion.qX,
            quatY = orientationFusion.qY,
            quatZ = orientationFusion.qZ
        )
    }

    // ==========================================
    // Interactive Simulation Mode
    // Allows testing in emulator or without hardware nearby
    // ==========================================

    fun toggleSimulation() {
        if (_controllerState.value.isSimulated) {
            stopSimulation()
        } else {
            startSimulation()
        }
    }

    fun startSimulation() {
        disconnect()
        stopScan()

        _controllerState.value = _controllerState.value.copy(
            connectionState = GearVRConnectionState.STREAMING,
            deviceName = "Gear VR Controller (Demo Mode)",
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            batteryPercent = 95,
            isSimulated = true,
            packetsPerSec = 60
        )

        simulationJob?.cancel()
        simulationJob = scope.launch {
            var t = 0f
            while (isActive) {
                delay(16L) // ~60 FPS
                t += 0.03f

                // Smoothly rotate Euler angles
                val pitch = kotlin.math.sin(t * 0.7f) * 35f
                val yaw = (t * 25f) % 360f - 180f
                val roll = kotlin.math.cos(t * 0.5f) * 20f

                // Calculate corresponding quaternion
                val pRad = Math.toRadians(pitch.toDouble() / 2)
                val yRad = Math.toRadians(yaw.toDouble() / 2)
                val rRad = Math.toRadians(roll.toDouble() / 2)

                val qW = (kotlin.math.cos(rRad) * kotlin.math.cos(pRad) * kotlin.math.cos(yRad) +
                        kotlin.math.sin(rRad) * kotlin.math.sin(pRad) * kotlin.math.sin(yRad)).toFloat()
                val qX = (kotlin.math.sin(rRad) * kotlin.math.cos(pRad) * kotlin.math.cos(yRad) -
                        kotlin.math.cos(rRad) * kotlin.math.sin(pRad) * kotlin.math.sin(yRad)).toFloat()
                val qY = (kotlin.math.cos(rRad) * kotlin.math.sin(pRad) * kotlin.math.cos(yRad) +
                        kotlin.math.sin(rRad) * kotlin.math.cos(pRad) * kotlin.math.sin(yRad)).toFloat()
                val qZ = (kotlin.math.cos(rRad) * kotlin.math.cos(pRad) * kotlin.math.sin(yRad) -
                        kotlin.math.sin(rRad) * kotlin.math.sin(pRad) * kotlin.math.cos(yRad)).toFloat()

                // Oscillate touch coordinates on touchpad
                val touchRadius = 100f
                val touchCenterX = 155f
                val touchCenterY = 155f
                val simTouchX = touchCenterX + kotlin.math.cos(t * 2f) * touchRadius
                val simTouchY = touchCenterY + kotlin.math.sin(t * 2f) * touchRadius

                val trigger = (kotlin.math.sin(t * 1.5f) > 0.6f)
                val padClick = (kotlin.math.sin(t * 1.0f) > 0.85f)

                _controllerState.value = _controllerState.value.copy(
                    quatW = qW,
                    quatX = qX,
                    quatY = qY,
                    quatZ = qZ,
                    pitchDeg = pitch,
                    yawDeg = yaw,
                    rollDeg = roll,
                    touchX = simTouchX,
                    touchY = simTouchY,
                    isTouching = true,
                    triggerPressed = trigger,
                    touchpadClicked = padClick,
                    temperatureC = 28,
                    packetsPerSec = 60,
                    lastPacketTimestamp = System.currentTimeMillis()
                )
            }
        }
    }

    fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
        if (_controllerState.value.isSimulated) {
            _controllerState.value = GearVRState(
                connectionState = GearVRConnectionState.DISCONNECTED
            )
        }
    }
}
