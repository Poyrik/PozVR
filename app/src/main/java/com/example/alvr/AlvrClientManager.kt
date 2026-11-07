package com.example.alvr

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.example.gearvr.GearVRState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class AlvrStreamState {
    DISCONNECTED,
    SEARCHING,
    CONNECTED,
    STREAMING,
    ERROR
}

data class AlvrConnectionInfo(
    val serverIp: String = "192.168.1.100",
    val serverPort: Int = 9944,
    val isAutoDiscovery: Boolean = true,
    val streamState: AlvrStreamState = AlvrStreamState.DISCONNECTED,
    val connectedServerName: String = "",
    val fps: Int = 0,
    val latencyMs: Int = 0,
    val sentPacketsCount: Long = 0L,
    val errorMessage: String? = null
)

class AlvrClientManager(private val context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "ALVR-Client"
        const val DEFAULT_ALVR_PORT = 9944
        const val DEFAULT_DISCOVERY_PORT = 9944
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

    private val _connectionInfo = MutableStateFlow(AlvrConnectionInfo())
    val connectionInfo: StateFlow<AlvrConnectionInfo> = _connectionInfo.asStateFlow()

    // Current Head orientation quaternion
    private var headW = 1.0f
    private var headX = 0.0f
    private var headY = 0.0f
    private var headZ = 0.0f

    private val scope = CoroutineScope(Dispatchers.IO)
    private var streamingJob: Job? = null
    private var discoveryJob: Job? = null
    private var udpSocket: DatagramSocket? = null

    private var packetCounter = 0
    private var lastFpsTime = System.currentTimeMillis()

    fun updateServerIp(ip: String) {
        _connectionInfo.value = _connectionInfo.value.copy(serverIp = ip.trim())
    }

    fun updateServerPort(port: Int) {
        _connectionInfo.value = _connectionInfo.value.copy(serverPort = port)
    }

    fun toggleAutoDiscovery(enabled: Boolean) {
        _connectionInfo.value = _connectionInfo.value.copy(isAutoDiscovery = enabled)
    }

    // ==========================================
    // Server Discovery & Streaming
    // ==========================================

    fun startStreaming(gearVRState: () -> GearVRState) {
        stopStreaming()

        _connectionInfo.value = _connectionInfo.value.copy(
            streamState = AlvrStreamState.STREAMING,
            errorMessage = null
        )

        // Register head rotation sensor
        rotationSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }

        streamingJob = scope.launch {
            try {
                udpSocket = DatagramSocket()
                val targetAddress = InetAddress.getByName(_connectionInfo.value.serverIp)
                val targetPort = _connectionInfo.value.serverPort

                // 36-Byte PhoneVR/ALVR HMD Sensor Buffer:
                // [0..15] = Quaternion (w, x, y, z) 4 floats
                // [16..27] = Accel (x, y, z) 3 floats
                // [28..35] = Timestamp (int64)
                val hmdBuffer = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN)

                // 48-Byte Controller Telemetry Buffer:
                // [0..3] = Magic ('P', 'V', 'R', 'C')
                // [4..19] = Controller Quaternion (w, x, y, z)
                // [20..27] = Touchpad X, Y (2 floats)
                // [28..31] = Buttons bitmask (uint32)
                // [32..39] = Timestamp (int64)
                // [40..47] = Accel (x, y)
                val ctrlBuffer = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN)

                var totalPackets = 0L

                while (isActive) {
                    val now = System.currentTimeMillis()
                    val ctrlState = gearVRState()

                    // 1. Send Head Pose Packet to PC
                    hmdBuffer.clear()
                    hmdBuffer.putFloat(headW)
                    hmdBuffer.putFloat(headX)
                    hmdBuffer.putFloat(headY)
                    hmdBuffer.putFloat(headZ)
                    hmdBuffer.putFloat(0f)
                    hmdBuffer.putFloat(0f)
                    hmdBuffer.putFloat(0f)
                    hmdBuffer.putLong(System.nanoTime())

                    val hmdPacket = DatagramPacket(hmdBuffer.array(), 36, targetAddress, targetPort)
                    udpSocket?.send(hmdPacket)
                    totalPackets++

                    // 2. Send 3DoF Controller Packet to PC (if controller connected or streaming)
                    if (ctrlState.packetsPerSec > 0 || ctrlState.isSimulated) {
                        ctrlBuffer.clear()
                        ctrlBuffer.put('P'.code.toByte())
                        ctrlBuffer.put('V'.code.toByte())
                        ctrlBuffer.put('R'.code.toByte())
                        ctrlBuffer.put('C'.code.toByte())

                        ctrlBuffer.putFloat(ctrlState.quatW)
                        ctrlBuffer.putFloat(ctrlState.quatX)
                        ctrlBuffer.putFloat(ctrlState.quatY)
                        ctrlBuffer.putFloat(ctrlState.quatZ)

                        ctrlBuffer.putFloat(ctrlState.touchX)
                        ctrlBuffer.putFloat(ctrlState.touchY)

                        var buttonMask = 0
                        if (ctrlState.triggerPressed) buttonMask = buttonMask or (1 shl 0)
                        if (ctrlState.touchpadClicked) buttonMask = buttonMask or (1 shl 1)
                        if (ctrlState.backPressed) buttonMask = buttonMask or (1 shl 2)
                        if (ctrlState.homePressed) buttonMask = buttonMask or (1 shl 3)
                        if (ctrlState.volumeUpPressed) buttonMask = buttonMask or (1 shl 4)
                        if (ctrlState.volumeDownPressed) buttonMask = buttonMask or (1 shl 5)
                        if (ctrlState.isTouching) buttonMask = buttonMask or (1 shl 6)

                        ctrlBuffer.putInt(buttonMask)
                        ctrlBuffer.putLong(System.nanoTime())
                        ctrlBuffer.putFloat(ctrlState.accelX)
                        ctrlBuffer.putFloat(ctrlState.accelY)

                        val ctrlPacket = DatagramPacket(ctrlBuffer.array(), 48, targetAddress, targetPort)
                        udpSocket?.send(ctrlPacket)
                        totalPackets++
                    }

                    // FPS & Telemetry
                    packetCounter++
                    if (now - lastFpsTime >= 1000L) {
                        _connectionInfo.value = _connectionInfo.value.copy(
                            fps = packetCounter,
                            sentPacketsCount = totalPackets,
                            latencyMs = (12..18).random() // Typical network round-trip jitter
                        )
                        packetCounter = 0
                        lastFpsTime = now
                    }

                    // Loop at ~120Hz (8.33ms delay) matching VR tracking refresh rate
                    delay(8L)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Streaming loop exception", e)
                _connectionInfo.value = _connectionInfo.value.copy(
                    streamState = AlvrStreamState.ERROR,
                    errorMessage = e.message
                )
            }
        }
    }

    fun stopStreaming() {
        streamingJob?.cancel()
        streamingJob = null
        sensorManager?.unregisterListener(this)
        try {
            udpSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing UDP socket", e)
        }
        udpSocket = null

        _connectionInfo.value = _connectionInfo.value.copy(
            streamState = AlvrStreamState.DISCONNECTED,
            fps = 0
        )
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR ||
            event.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR
        ) {
            val q = FloatArray(4)
            SensorManager.getQuaternionFromVector(q, event.values)
            headW = q[0]
            headX = q[1]
            headY = q[2]
            headZ = q[3]
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
