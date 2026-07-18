package com.nofar.core.sensors

import android.hardware.SensorManager
import android.view.Surface
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Remaps the rotation vector matrix for the current display rotation so azimuth and pitch
 * match the on-screen camera view (portrait vs landscape).
 */
internal object OrientationCoordinateRemapper {
    fun remapForDisplayRotation(rotationMatrix: FloatArray, displayRotation: Int): FloatArray {
        val remapped = FloatArray(9)
        val remappedSuccessfully =
            when (displayRotation) {
                Surface.ROTATION_0 ->
                    SensorManager.remapCoordinateSystem(
                        rotationMatrix,
                        SensorManager.AXIS_X,
                        SensorManager.AXIS_Z,
                        remapped
                    )
                Surface.ROTATION_90 ->
                    SensorManager.remapCoordinateSystem(
                        rotationMatrix,
                        SensorManager.AXIS_Y,
                        SensorManager.AXIS_MINUS_X,
                        remapped
                    )
                Surface.ROTATION_180 ->
                    SensorManager.remapCoordinateSystem(
                        rotationMatrix,
                        SensorManager.AXIS_MINUS_X,
                        SensorManager.AXIS_MINUS_Z,
                        remapped
                    )
                Surface.ROTATION_270 ->
                    SensorManager.remapCoordinateSystem(
                        rotationMatrix,
                        SensorManager.AXIS_MINUS_Y,
                        SensorManager.AXIS_X,
                        remapped
                    )
                else -> false
            }
        return if (remappedSuccessfully) remapped else rotationMatrix
    }

    fun orientationAngles(rotationMatrix: FloatArray, displayRotation: Int): FloatArray {
        val remapped = remapForDisplayRotation(rotationMatrix, displayRotation)
        val angles = FloatArray(3)
        SensorManager.getOrientation(remapped, angles)
        return angles
    }

    /**
     * Elevation of the back-camera look direction above the horizontal plane.
     * Device +Z points toward the user; the back camera looks along device -Z.
     *
     * Uses the raw [SensorManager.getRotationMatrixFromVector] output — display remapping
     * is for compass azimuth only and must not be applied here.
     */
    fun backCameraElevationDeg(rotationMatrix: FloatArray): Float {
        val lookX = -rotationMatrix[2].toDouble()
        val lookY = -rotationMatrix[5].toDouble()
        val lookZ = -rotationMatrix[8].toDouble()
        return Math.toDegrees(atan2(lookZ, hypot(lookX, lookY))).toFloat()
    }
}
