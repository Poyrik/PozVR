package com.example.gearvr

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 3DoF Sensor Fusion filter for the Samsung Gear VR Controller.
 * Fuses 3-axis accelerometer and 3-axis gyroscope data into a smooth,
 * drift-compensated unit quaternion (w, x, y, z) and Euler angles.
 */
class OrientationFusion {

    // Quaternion components (w, x, y, z)
    var qW = 1.0f
        private set
    var qX = 0.0f
        private set
    var qY = 0.0f
        private set
    var qZ = 0.0f
        private set

    // Re-center offset quaternion
    private var centerW = 1.0f
    private var centerX = 0.0f
    private var centerY = 0.0f
    private var centerZ = 0.0f

    // Euler angles in degrees
    var pitch = 0.0f
        private set
    var yaw = 0.0f
        private set
    var roll = 0.0f
        private set

    // Gyroscope bias estimation
    private var biasX = 0.0f
    private var biasY = 0.0f
    private var biasZ = 0.0f
    private var stationaryCount = 0

    // Filter tuning: alpha closer to 1 relies more on gyro; (1-alpha) on accelerometer gravity
    private val alpha = 0.98f

    /**
     * Updates the orientation using a sensor sample.
     * @param ax, ay, az Accelerometer reading in m/s^2
     * @param gx, gy, gz Gyroscope reading in rad/s
     * @param dt Delta time in seconds since last update
     */
    fun update(ax: Float, ay: Float, az: Float, gx: Float, gy: Float, gz: Float, dt: Float) {
        if (dt <= 0.0f || dt > 0.5f) return

        // 1. Gyro Bias Auto-Calibration during stationary moments
        val gyroNorm = sqrt(gx * gx + gy * gy + gz * gz)
        val accelNorm = sqrt(ax * ax + ay * ay + az * az)

        // If total acceleration is close to 1G (~9.81 m/s^2) and gyro is quiet, calibrate bias
        if (accelNorm in 8.5f..11.0f && gyroNorm < 0.08f) {
            stationaryCount++
            val learningRate = 0.01f
            biasX += (gx - biasX) * learningRate
            biasY += (gy - biasY) * learningRate
            biasZ += (gz - biasZ) * learningRate
        }

        val correctedGx = gx - biasX
        val correctedGy = gy - biasY
        val correctedGz = gz - biasZ

        // 2. Gyroscope Integration (Small angle approximation)
        val halfDt = 0.5f * dt
        val dqW = 1.0f
        val dqX = correctedGx * halfDt
        val dqY = correctedGy * halfDt
        val dqZ = correctedGz * halfDt

        // Multiply current quaternion by delta quaternion
        var newW = qW * dqW - qX * dqX - qY * dqY - qZ * dqZ
        var newX = qW * dqX + qX * dqW + qY * dqZ - qZ * dqY
        var newY = qW * dqY - qX * dqZ + qY * dqW + qZ * dqX
        var newZ = qW * dqZ + qX * dqY - qY * dqX + qZ * dqW

        // 3. Accelerometer Tilt Correction (Gravity alignment for Pitch & Roll)
        if (accelNorm > 0.1f) {
            val invAccelNorm = 1.0f / accelNorm
            val normAx = ax * invAccelNorm
            val normAy = ay * invAccelNorm
            val normAz = az * invAccelNorm

            // Estimated gravity vector from current quaternion: R(q)^T * [0, 0, 1]
            val vx = 2.0f * (newX * newZ - newW * newY)
            val vy = 2.0f * (newW * newX + newY * newZ)
            val vz = newW * newW - newX * newX - newY * newY + newZ * newZ

            // Error is the cross product between estimated gravity and measured acceleration
            val ex = (normAy * vz - normAz * vy)
            val ey = (normAz * vx - normAx * vz)
            val ez = (normAx * vy - normAy * vx)

            // Complementary correction factor
            val beta = (1.0f - alpha) * 2.0f
            newX += ex * beta * dt
            newY += ey * beta * dt
            newZ += ez * beta * dt
        }

        // 4. Normalize the Quaternion
        val norm = sqrt(newW * newW + newX * newX + newY * newY + newZ * newZ)
        if (norm > 0.0001f) {
            val invNorm = 1.0f / norm
            qW = newW * invNorm
            qX = newX * invNorm
            qY = newY * invNorm
            qZ = newZ * invNorm
        }

        // 5. Compute Euler angles
        computeEulerAngles()
    }

    /**
     * Re-centers the forward orientation (sets current yaw to 0).
     */
    fun reCenter() {
        // Invert current quaternion to cancel it out
        centerW = qW
        centerX = -qX
        centerY = -qY
        centerZ = -qZ
    }

    /**
     * Resets the filter to identity quaternion.
     */
    fun reset() {
        qW = 1.0f
        qX = 0.0f
        qY = 0.0f
        qZ = 0.0f
        centerW = 1.0f
        centerX = 0.0f
        centerY = 0.0f
        centerZ = 0.0f
        pitch = 0.0f
        yaw = 0.0f
        roll = 0.0f
        biasX = 0.0f
        biasY = 0.0f
        biasZ = 0.0f
    }

    private fun computeEulerAngles() {
        // Roll (x-axis rotation)
        val sinrCosp = 2.0f * (qW * qX + qY * qZ)
        val cosrCosp = 1.0f - 2.0f * (qX * qX + qY * qY)
        roll = Math.toDegrees(atan2(sinrCosp.toDouble(), cosrCosp.toDouble())).toFloat()

        // Pitch (y-axis rotation)
        val sinp = 2.0f * (qW * qY - qZ * qX)
        pitch = if (kotlin.math.abs(sinp) >= 1.0f) {
            Math.toDegrees(Math.copySign(Math.PI / 2, sinp.toDouble())).toFloat()
        } else {
            Math.toDegrees(kotlin.math.asin(sinp.toDouble())).toFloat()
        }

        // Yaw (z-axis rotation)
        val sinyCosp = 2.0f * (qW * qZ + qX * qY)
        val cosyCosp = 1.0f - 2.0f * (qY * qY + qZ * qZ)
        yaw = Math.toDegrees(atan2(sinyCosp.toDouble(), cosyCosp.toDouble())).toFloat()
    }
}
