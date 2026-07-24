package com.nofar.core.visibility

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.AppConfig
import org.junit.Test

class HorizonProjectorTest {
    private val profile =
        HorizonProfile(
            azimuthStepDeg = AppConfig.HORIZON_AZIMUTH_STEP_DEG,
            elevationAnglesDeg = FloatArray((360f / AppConfig.HORIZON_AZIMUTH_STEP_DEG).toInt()) { 5f }
        )

    @Test
    fun centerBucketAtDeviceHeading_isProjectedToScreenCenter() {
        val points =
            HorizonProjector.project(
                profile = profile,
                trueAzimuthDeg = 0f,
                cameraElevationDeg = 5f,
                fov = CameraFieldOfView(horizontalDeg = 60f, verticalDeg = 45f),
                screenWidthPx = 1080f,
                screenHeightPx = 1920f
            )

        assertThat(points).isNotEmpty()
        val centerPoint = points.minByOrNull { kotlin.math.abs(it.xPx - 540f) }!!
        assertThat(centerPoint.xPx).isWithin(1f).of(540f)
        assertThat(centerPoint.yPx).isWithin(1f).of(960f)
    }

    @Test
    fun wraparoundWindow_includesBucketsAcrossZeroDegrees() {
        val wrapProfile =
            HorizonProfile(
                azimuthStepDeg = 10f,
                elevationAnglesDeg = FloatArray(36) { index -> index.toFloat() }
            )
        val points =
            HorizonProjector.project(
                profile = wrapProfile,
                trueAzimuthDeg = 350f,
                cameraElevationDeg = 0f,
                fov = CameraFieldOfView(horizontalDeg = 40f, verticalDeg = 30f),
                screenWidthPx = 1000f,
                screenHeightPx = 2000f
            )

        assertThat(points.size).isGreaterThan(4)
        assertThat(points.map { it.xPx }.minOrNull()).isLessThan(500f)
        assertThat(points.map { it.xPx }.maxOrNull()).isGreaterThan(500f)
    }

    @Test
    fun screenPadding_includesPointsBeyondStrictHorizontalFov() {
        val points =
            HorizonProjector.project(
                profile = profile,
                trueAzimuthDeg = 0f,
                cameraElevationDeg = 5f,
                fov = CameraFieldOfView(horizontalDeg = 60f, verticalDeg = 45f),
                screenWidthPx = 1080f,
                screenHeightPx = 1920f
            )

        assertThat(points.map { it.xPx }.minOrNull()).isLessThan(0f)
        assertThat(points.map { it.xPx }.maxOrNull()).isGreaterThan(1080f)
    }

    @Test
    fun projectedPoints_followMonotonicScreenXAcrossNorthWrap() {
        val variedProfile =
            HorizonProfile(
                azimuthStepDeg = 2f,
                elevationAnglesDeg = FloatArray(180) { index -> (index % 20).toFloat() }
            )
        val points =
            HorizonProjector.project(
                profile = variedProfile,
                trueAzimuthDeg = 347f,
                cameraElevationDeg = 0f,
                fov = CameraFieldOfView(horizontalDeg = 60f, verticalDeg = 45f),
                screenWidthPx = 1080f,
                screenHeightPx = 1920f
            )

        assertThat(points.size).isGreaterThan(4)
        points.zipWithNext { previous, next ->
            assertThat(next.xPx).isAtLeast(previous.xPx - 0.01f)
        }
    }

    @Test
    fun azimuthSamples_crossingNorth_stepsThroughContinuousAngles() {
        val samples = HorizonProjector.azimuthSamples(minAzimuthDeg = 316f, maxAzimuthDeg = 384f, stepDeg = 2f)

        assertThat(samples.first()).isWithin(0.001f).of(316f)
        assertThat(samples.last()).isWithin(0.001f).of(384f)
        assertThat(samples).contains(358f)
        assertThat(samples).contains(360f)
    }

    @Test
    fun orientationChange_reprojectsSameProfileToDifferentPoints() {
        val variedProfile =
            HorizonProfile(
                azimuthStepDeg = AppConfig.HORIZON_AZIMUTH_STEP_DEG,
                elevationAnglesDeg =
                FloatArray((360f / AppConfig.HORIZON_AZIMUTH_STEP_DEG).toInt()) { index ->
                    index.toFloat()
                }
            )
        val northFacing =
            HorizonProjector.project(
                profile = variedProfile,
                trueAzimuthDeg = 0f,
                cameraElevationDeg = 0f,
                fov = CameraFieldOfView(horizontalDeg = 60f, verticalDeg = 45f),
                screenWidthPx = 1080f,
                screenHeightPx = 1920f
            )
        val eastFacing =
            HorizonProjector.project(
                profile = variedProfile,
                trueAzimuthDeg = 90f,
                cameraElevationDeg = 0f,
                fov = CameraFieldOfView(horizontalDeg = 60f, verticalDeg = 45f),
                screenWidthPx = 1080f,
                screenHeightPx = 1920f
            )

        assertThat(northFacing).isNotEmpty()
        assertThat(eastFacing).isNotEmpty()
        assertThat(northFacing.map { it.xPx to it.yPx })
            .isNotEqualTo(eastFacing.map { it.xPx to it.yPx })
    }
}
