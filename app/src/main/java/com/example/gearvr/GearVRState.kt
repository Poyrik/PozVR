package com.example.gearvr

/**
 * Connection states for the Samsung Gear VR Bluetooth controller.
 */
enum class GearVRConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    STREAMING,
    ERROR
}

/**
 * Representation of a discovered or bonded Bluetooth LE device.
 */
data class BleDeviceItem(
    val name: String,
    val address: String,
    val rssi: Int = 0,
    val isBonded: Boolean = false,
    val isGearVRController: Boolean = false
)

/**
 * Complete real-time telemetry state of the Gear VR 3DoF Controller.
 */
data class GearVRState(
    val connectionState: GearVRConnectionState = GearVRConnectionState.DISCONNECTED,
    val deviceName: String = "",
    val deviceAddress: String = "",
    val batteryPercent: Int = -1,

    // 3DoF Orientation Quaternion (normalized: w, x, y, z)
    val quatW: Float = 1.0f,
    val quatX: Float = 0.0f,
    val quatY: Float = 0.0f,
    val quatZ: Float = 0.0f,

    // Euler angles in degrees for intuitive visual display
    val pitchDeg: Float = 0.0f,
    val yawDeg: Float = 0.0f,
    val rollDeg: Float = 0.0f,

    // Hardware Buttons
    val triggerPressed: Boolean = false,
    val touchpadClicked: Boolean = false,
    val backPressed: Boolean = false,
    val homePressed: Boolean = false,
    val volumeUpPressed: Boolean = false,
    val volumeDownPressed: Boolean = false,

    // Touchpad 2D Coordinates (0..315) and touching flag
    val touchX: Float = 0.0f,
    val touchY: Float = 0.0f,
    val isTouching: Boolean = false,

    // Raw IMU Sensor Readings
    val accelX: Float = 0.0f,
    val accelY: Float = 0.0f,
    val accelZ: Float = 0.0f,
    val gyroX: Float = 0.0f,
    val gyroY: Float = 0.0f,
    val gyroZ: Float = 0.0f,

    // Diagnostics
    val temperatureC: Int = 0,
    val packetsPerSec: Int = 0,
    val lastPacketTimestamp: Long = 0L,
    val isSimulated: Boolean = false,
    val errorMessage: String? = null
)
