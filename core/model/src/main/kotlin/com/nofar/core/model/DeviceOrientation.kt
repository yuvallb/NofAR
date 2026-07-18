package com.nofar.core.model

/**
 * Device attitude in true-north frame after declination correction (Requirements §3.3).
 */
data class DeviceOrientation(
    val trueAzimuthDeg: Float,
    val pitchDeg: Float,
    val rollDeg: Float,
    /** Back-camera elevation above the horizontal plane, in degrees. */
    val cameraElevationDeg: Float,
    /** [android.hardware.SensorManager] accuracy constant for the rotation vector sensor. */
    val accuracy: Int,
    val timestampNanos: Long
)
