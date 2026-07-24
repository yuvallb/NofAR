package com.nofar.feature.explore

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.AppConfig
import com.nofar.core.model.DeviceOrientation
import com.nofar.core.visibility.CameraFieldOfView
import com.nofar.core.visibility.HorizonProfile
import com.nofar.core.visibility.HorizonProjector
import org.junit.Test

/**
 * Mirrors the horizon portion of [ExploreViewModel.reprojectOverlay] without spinning up the full ViewModel graph.
 */
class ExploreHorizonOverlayTest {
    private val profile =
        HorizonProfile(
            azimuthStepDeg = AppConfig.HORIZON_AZIMUTH_STEP_DEG,
            elevationAnglesDeg =
            FloatArray((360f / AppConfig.HORIZON_AZIMUTH_STEP_DEG).toInt()) { index ->
                index.toFloat()
            }
        )

    @Test
    fun showHorizonOutlineDisabled_yieldsEmptyLinePoints() {
        val points =
            projectHorizonLine(
                showHorizonOutline = false,
                profile = profile,
                orientation = sampleOrientation(azimuthDeg = 0f)
            )

        assertThat(points).isEmpty()
    }

    @Test
    fun orientationChange_reprojectsCachedProfileWithoutRecomputingIt() {
        val north =
            projectHorizonLine(
                showHorizonOutline = true,
                profile = profile,
                orientation = sampleOrientation(azimuthDeg = 0f)
            )
        val east =
            projectHorizonLine(
                showHorizonOutline = true,
                profile = profile,
                orientation = sampleOrientation(azimuthDeg = 90f)
            )

        assertThat(north).isNotEmpty()
        assertThat(east).isNotEmpty()
        assertThat(north.map { it.xPx to it.yPx }).isNotEqualTo(east.map { it.xPx to it.yPx })
    }

    private fun projectHorizonLine(
        showHorizonOutline: Boolean,
        profile: HorizonProfile?,
        orientation: DeviceOrientation
    ) = if (showHorizonOutline) {
        profile?.let {
            HorizonProjector.project(
                profile = it,
                trueAzimuthDeg = orientation.trueAzimuthDeg,
                cameraElevationDeg = orientation.cameraElevationDeg,
                fov = CameraFieldOfView(horizontalDeg = 60f, verticalDeg = 45f),
                screenWidthPx = 1080f,
                screenHeightPx = 1920f
            )
        } ?: emptyList()
    } else {
        emptyList()
    }

    private fun sampleOrientation(azimuthDeg: Float): DeviceOrientation = DeviceOrientation(
        trueAzimuthDeg = azimuthDeg,
        pitchDeg = 0f,
        rollDeg = 0f,
        cameraElevationDeg = 4f,
        accuracy = 3,
        timestampNanos = 0L
    )
}
