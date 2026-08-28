package com.nofar.core.sensors

import com.google.common.truth.Truth.assertThat
import kotlin.math.cos
import kotlin.math.hypot
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

    @Test
    fun backCameraAzimuthDeg_lookingNorth_isZero() {
        val azimuth =
            OrientationCoordinateRemapper.backCameraAzimuthDeg(
                rotationMatrixForLookAzimuthDeg(azimuthDeg = 0.0, elevationDeg = 0.0)
            )

        assertThat(azimuth).isWithin(0.1f).of(0f)
    }

    @Test
    fun backCameraAzimuthDeg_lookingEast_isNinety() {
        val azimuth =
            OrientationCoordinateRemapper.backCameraAzimuthDeg(
                rotationMatrixForLookAzimuthDeg(azimuthDeg = 90.0, elevationDeg = 0.0)
            )

        assertThat(azimuth).isWithin(0.1f).of(90f)
    }

    @Test
    fun backCameraAzimuthDeg_lookingWest_isTwoSeventy() {
        val azimuth =
            OrientationCoordinateRemapper.backCameraAzimuthDeg(
                rotationMatrixForLookAzimuthDeg(azimuthDeg = 270.0, elevationDeg = 0.0)
            )

        assertThat(azimuth).isWithin(0.1f).of(270f)
    }

    /**
     * Builds a world-from-device matrix where device -Z (back camera) points at [elevationDeg].
     */
    private fun rotationMatrixForCameraElevationDeg(elevationDeg: Double): FloatArray =
        rotationMatrixForLookAzimuthDeg(azimuthDeg = 0.0, elevationDeg = elevationDeg)

    /**
     * World-from-device matrix: device Y is up, -Z looks at [azimuthDeg] clockwise from north
     * at [elevationDeg] above the horizon.
     */
    private fun rotationMatrixForLookAzimuthDeg(azimuthDeg: Double, elevationDeg: Double): FloatArray {
        val azimuthRad = Math.toRadians(azimuthDeg)
        val elevationRad = Math.toRadians(elevationDeg)
        val lookEast = sin(azimuthRad) * cos(elevationRad)
        val lookNorth = cos(azimuthRad) * cos(elevationRad)
        val lookUp = sin(elevationRad)
        // Device X = right = look × world-up, then Y = X × look so Y is camera-up.
        val rightEast = lookNorth
        val rightNorth = -lookEast
        val rightLen = hypot(rightEast, rightNorth).coerceAtLeast(1e-9)
        val rightE = rightEast / rightLen
        val rightN = rightNorth / rightLen
        val upE = rightN * lookUp
        val upN = -rightE * lookUp
        val upU = rightE * lookNorth - rightN * lookEast
        return floatArrayOf(
            rightE.toFloat(),
            upE.toFloat(),
            (-lookEast).toFloat(),
            rightN.toFloat(),
            upN.toFloat(),
            (-lookNorth).toFloat(),
            0f,
            upU.toFloat(),
            (-lookUp).toFloat()
        )
    }
}
