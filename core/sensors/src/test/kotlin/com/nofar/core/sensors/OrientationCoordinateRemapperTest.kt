package com.nofar.core.sensors

import com.google.common.truth.Truth.assertThat
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Test

class OrientationCoordinateRemapperTest {
    @Test
    fun backCameraElevationDeg_levelPhone_isNearZero() {
        val elevation =
            OrientationCoordinateRemapper.backCameraElevationDeg(
                rotationMatrixForCameraElevationDeg(elevationDeg = 0.0)
            )

        assertThat(elevation).isWithin(0.1f).of(0f)
    }

    @Test
    fun backCameraElevationDeg_tiltedUp_isPositive() {
        val elevation =
            OrientationCoordinateRemapper.backCameraElevationDeg(
                rotationMatrixForCameraElevationDeg(elevationDeg = 15.0)
            )

        assertThat(elevation).isWithin(0.1f).of(15f)
    }

    @Test
    fun backCameraElevationDeg_tiltedDown_isNegative() {
        val elevation =
            OrientationCoordinateRemapper.backCameraElevationDeg(
                rotationMatrixForCameraElevationDeg(elevationDeg = -20.0)
            )

        assertThat(elevation).isWithin(0.1f).of(-20f)
    }

    /**
     * Builds a world-from-device matrix where device -Z (back camera) points at [elevationDeg].
     */
    private fun rotationMatrixForCameraElevationDeg(elevationDeg: Double): FloatArray {
        val elevationRad = Math.toRadians(elevationDeg)
        val lookX = cos(elevationRad)
        val lookZ = sin(elevationRad)
        return floatArrayOf(
            -lookZ.toFloat(),
            0f,
            -lookX.toFloat(),
            0f,
            1f,
            0f,
            lookX.toFloat(),
            0f,
            -lookZ.toFloat()
        )
    }
}
