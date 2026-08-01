package com.nofar.feature.explore

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.AppConfig
import com.nofar.core.model.DeviceOrientation
import com.nofar.core.visibility.CameraFieldOfView
import com.nofar.core.visibility.HorizonProfile
import com.nofar.core.visibility.HorizonProjector
import com.nofar.core.visibility.HorizonScreenPolyline
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
                (index % 10).toFloat()
            }
        )

    @Test
    fun showHorizonOutlineDisabled_yieldsEmptyLinePoints() {
        val segments =
            projectHorizonLine(
                showHorizonOutline = false,
                profile = profile,
                orientation = sampleOrientation(azimuthDeg = 0f)
            )

        assertThat(segments).isEmpty()
    }

    // H-P2-02: when Explore is not fully live the whole overlay is cleared — a cached profile must not
    // leak a stroke. Asserts the extracted gate the ViewModel uses to clear horizonLineSegments.
    @Test
    fun overlayGate_closedStates_blockProjectionEvenWithCachedProfile() {
        val closedStates =
            listOf(
                // not READY
                ExplorePreconditions.canProjectOverlay(
                    hasOrientation = true,
                    screenWidthPx = 1080f,
                    screenHeightPx = 1920f,
                    gate = ExploreGate.WAITING_GPS,
                    locationAccuracyDegraded = false
                ),
                // degraded GPS accuracy
                ExplorePreconditions.canProjectOverlay(
                    hasOrientation = true,
                    screenWidthPx = 1080f,
                    screenHeightPx = 1920f,
                    gate = ExploreGate.READY,
                    locationAccuracyDegraded = true
                ),
                // no orientation yet
                ExplorePreconditions.canProjectOverlay(
                    hasOrientation = false,
                    screenWidthPx = 1080f,
                    screenHeightPx = 1920f,
                    gate = ExploreGate.READY,
                    locationAccuracyDegraded = false
                ),
                // surface not measured
                ExplorePreconditions.canProjectOverlay(
                    hasOrientation = true,
                    screenWidthPx = 0f,
                    screenHeightPx = 0f,
                    gate = ExploreGate.READY,
                    locationAccuracyDegraded = false
                )
            )

        assertThat(closedStates).containsExactly(false, false, false, false)
        assertThat(
            ExplorePreconditions.canProjectOverlay(
                hasOrientation = true,
                screenWidthPx = 1080f,
                screenHeightPx = 1920f,
                gate = ExploreGate.READY,
                locationAccuracyDegraded = false
            )
        ).isTrue()
    }

    // H-P2-03: the preference gates the outline before the profile is even read — no scheduler cache is
    // required when the toggle is off. The profile provider flips a flag if the disabled path touches it.
    @Test
    fun preferenceOff_doesNotReadCachedProfile() {
        var profileRead = false
        val segments =
            projectHorizonLineLazily(
                showHorizonOutline = false,
                profileProvider = {
                    profileRead = true
                    profile
                },
                orientation = sampleOrientation(azimuthDeg = 0f)
            )

        assertThat(segments).isEmpty()
        assertThat(profileRead).isFalse()
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
        assertThat(flatten(north)).isNotEqualTo(flatten(east))
    }

    private fun projectHorizonLine(
        showHorizonOutline: Boolean,
        profile: HorizonProfile?,
        orientation: DeviceOrientation
    ): List<HorizonScreenPolyline> = if (showHorizonOutline) {
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

    private fun projectHorizonLineLazily(
        showHorizonOutline: Boolean,
        profileProvider: () -> HorizonProfile?,
        orientation: DeviceOrientation
    ): List<HorizonScreenPolyline> {
        val profile = if (showHorizonOutline) profileProvider() else null
        return if (profile == null) {
            emptyList()
        } else {
            HorizonProjector.project(
                profile = profile,
                trueAzimuthDeg = orientation.trueAzimuthDeg,
                cameraElevationDeg = orientation.cameraElevationDeg,
                fov = CameraFieldOfView(horizontalDeg = 60f, verticalDeg = 45f),
                screenWidthPx = 1080f,
                screenHeightPx = 1920f
            )
        }
    }

    private fun flatten(segments: List<HorizonScreenPolyline>) =
        segments.flatMap { segment -> segment.points.map { point -> point.xPx to point.yPx } }

    private fun sampleOrientation(azimuthDeg: Float): DeviceOrientation = DeviceOrientation(
        trueAzimuthDeg = azimuthDeg,
        pitchDeg = 0f,
        rollDeg = 0f,
        cameraElevationDeg = 4f,
        accuracy = 3,
        timestampNanos = 0L
    )
}
