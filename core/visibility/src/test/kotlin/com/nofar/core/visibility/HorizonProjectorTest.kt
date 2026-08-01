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
        val points = flatten(project(profile, trueAzimuthDeg = 0f, cameraElevationDeg = 5f))

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
                elevationAnglesDeg = FloatArray(36) { index -> (index % 10).toFloat() }
            )
        val points = flatten(project(wrapProfile, trueAzimuthDeg = 350f, cameraElevationDeg = 0f, horizontalFov = 40f))

        assertThat(points.size).isGreaterThan(4)
        assertThat(points.map { it.xPx }.minOrNull()).isLessThan(500f)
        assertThat(points.map { it.xPx }.maxOrNull()).isGreaterThan(500f)
    }

    @Test
    fun projectedPoints_stayWithinHorizontalScreenBounds() {
        val variedProfile =
            HorizonProfile(
                azimuthStepDeg = 2f,
                elevationAnglesDeg = FloatArray(180) { index -> (index % 20).toFloat() }
            )
        val points =
            flatten(
                project(
                    profile = variedProfile,
                    trueAzimuthDeg = 207f,
                    cameraElevationDeg = 0f,
                    horizontalFov = 60f
                )
            )

        assertThat(points).isNotEmpty()
        assertThat(points.map { it.xPx }.minOrNull()).isAtLeast(0f)
        assertThat(points.map { it.xPx }.maxOrNull()).isAtMost(1080f)
        assertThat(points.first().xPx).isWithin(1f).of(0f)
        assertThat(points.last().xPx).isWithin(1f).of(1080f)
    }

    @Test
    fun projectedPoints_followMonotonicScreenXAcrossNorthWrap() {
        val variedProfile =
            HorizonProfile(
                azimuthStepDeg = 2f,
                elevationAnglesDeg = FloatArray(180) { index -> (index % 20).toFloat() }
            )
        val points =
            flatten(
                project(
                    profile = variedProfile,
                    trueAzimuthDeg = 347f,
                    cameraElevationDeg = 0f,
                    horizontalFov = 60f
                )
            )

        assertThat(points.size).isGreaterThan(4)
        points.zipWithNext { previous, next ->
            assertThat(next.xPx).isAtLeast(previous.xPx - 0.01f)
        }
    }

    @Test
    fun flatProfile_levelCamera_projectsNearVerticalCenter() {
        val flatProfile =
            HorizonProfile(
                azimuthStepDeg = AppConfig.HORIZON_AZIMUTH_STEP_DEG,
                elevationAnglesDeg =
                FloatArray((360f / AppConfig.HORIZON_AZIMUTH_STEP_DEG).toInt()) { -1f }
            )
        val points =
            flatten(
                project(
                    profile = flatProfile,
                    trueAzimuthDeg = 203f,
                    cameraElevationDeg = 0f,
                    horizontalFov = 60f
                )
            )

        assertThat(points).isNotEmpty()
        val averageY = points.map { it.yPx }.average().toFloat()
        assertThat(averageY).isGreaterThan(768f)
        assertThat(averageY).isLessThan(1152f)
    }

    // H-DEC-1 Option A: skyline entirely above the frustum is omitted, not pinned to the top edge.
    @Test
    fun steepProfileBeyondVerticalFov_returnsEmpty() {
        val steepProfile =
            HorizonProfile(
                azimuthStepDeg = AppConfig.HORIZON_AZIMUTH_STEP_DEG,
                elevationAnglesDeg = FloatArray((360f / AppConfig.HORIZON_AZIMUTH_STEP_DEG).toInt()) { 80f }
            )
        val segments =
            project(
                profile = steepProfile,
                trueAzimuthDeg = 203f,
                cameraElevationDeg = 0f,
                horizontalFov = 60f,
                verticalFov = 45f
            )

        assertThat(segments).isEmpty()
    }

    @Test
    fun flatProfile_levelCamera_alwaysProjectsHorizonLine() {
        val flatProfile =
            HorizonProfile(
                azimuthStepDeg = AppConfig.HORIZON_AZIMUTH_STEP_DEG,
                elevationAnglesDeg =
                FloatArray((360f / AppConfig.HORIZON_AZIMUTH_STEP_DEG).toInt()) { -0.5f }
            )
        val segments =
            project(
                profile = flatProfile,
                trueAzimuthDeg = 203f,
                cameraElevationDeg = 0f,
                horizontalFov = 70.7f,
                verticalFov = 56.2f
            )

        assertThat(segments).isNotEmpty()
        assertThat(segments.sumOf { it.points.size }).isGreaterThan(10)
    }

    // H-P0-T01: flat DEM + level camera → smooth near-mid-screen skyline (no sky-only / bar regressions).
    @Test
    fun flatProfile_levelCamera_projectsSmoothMidScreenLine() {
        val flatProfile =
            HorizonProfile(
                azimuthStepDeg = AppConfig.HORIZON_AZIMUTH_STEP_DEG,
                elevationAnglesDeg =
                FloatArray((360f / AppConfig.HORIZON_AZIMUTH_STEP_DEG).toInt()) { -0.5f }
            )
        val segments =
            project(
                profile = flatProfile,
                trueAzimuthDeg = 203f,
                cameraElevationDeg = 0f,
                horizontalFov = 70.7f,
                verticalFov = 56.2f
            )
        val points = flatten(segments)

        assertThat(points).isNotEmpty()
        val meanY = points.map { it.yPx }.average().toFloat()
        assertThat(meanY).isAtLeast(0.35f * SCREEN_HEIGHT)
        assertThat(meanY).isAtMost(0.65f * SCREEN_HEIGHT)
        points.zipWithNext { previous, next ->
            assertThat(kotlin.math.abs(next.yPx - previous.yPx)).isLessThan(0.05f * SCREEN_HEIGHT)
        }
    }

    // H-P0-T02: pitching up must not be the unique condition that reveals the line. At level the line is
    // present; pitched +30° past the frustum it is omitted (H-DEC-1 Option A / H-DEC-2 "no").
    @Test
    fun flatProfile_pitchUp_omitsLineAndIsNotSkyOnly() {
        val flatProfile =
            HorizonProfile(
                azimuthStepDeg = AppConfig.HORIZON_AZIMUTH_STEP_DEG,
                elevationAnglesDeg =
                FloatArray((360f / AppConfig.HORIZON_AZIMUTH_STEP_DEG).toInt()) { -0.5f }
            )
        val level =
            project(
                profile = flatProfile,
                trueAzimuthDeg = 203f,
                cameraElevationDeg = 0f,
                horizontalFov = 70.7f,
                verticalFov = 56.2f
            )
        val pitchedUp =
            project(
                profile = flatProfile,
                trueAzimuthDeg = 203f,
                cameraElevationDeg = 30f,
                horizontalFov = 70.7f,
                verticalFov = 56.2f
            )

        assertThat(level).isNotEmpty()
        assertThat(pitchedUp).isEmpty()
        // The sky-only bug is: empty at level yet drawn only when pitched up. Assert that never happens.
        assertThat(level.isEmpty() && pitchedUp.isNotEmpty()).isFalse()
    }

    // H-P0-T03: an out-of-frustum spike is omitted (segment breaks) instead of clamping to the edge and
    // drawing a full-height vertical connector (the "yellow vertical loops" bug).
    @Test
    fun spikeBeyondVerticalFov_omitsSpikeWithoutEdgePinning() {
        val bucketCount = (360f / AppConfig.HORIZON_AZIMUTH_STEP_DEG).toInt()
        val spikeProfile =
            HorizonProfile(
                azimuthStepDeg = AppConfig.HORIZON_AZIMUTH_STEP_DEG,
                elevationAnglesDeg =
                FloatArray(bucketCount) { index ->
                    if (index == bucketCount / 2) 40f else 0f
                }
            )
        val segments =
            project(
                profile = spikeProfile,
                trueAzimuthDeg = spikeProfile.azimuthDegForIndex(bucketCount / 2),
                cameraElevationDeg = 0f,
                horizontalFov = 60f,
                verticalFov = 45f
            )
        val points = flatten(segments)

        assertThat(points).isNotEmpty()
        // No clamping to a screen edge: every point stays strictly inside the frustum.
        points.forEach { point ->
            assertThat(point.yPx).isGreaterThan(1f)
            assertThat(point.yPx).isLessThan(SCREEN_HEIGHT - 1f)
        }
        // The omitted spike splits the baseline into separate polylines rather than one looping stroke.
        assertThat(segments.size).isAtLeast(2)
    }

    @Test
    fun sampleElevationDeg_interpolatesAcrossNorthWrap() {
        val wrapProfile =
            HorizonProfile(
                azimuthStepDeg = 10f,
                elevationAnglesDeg = FloatArray(36) { index -> index.toFloat() }
            )

        assertThat(wrapProfile.sampleElevationDeg(355f)).isWithin(0.01f).of(17.5f)
        assertThat(wrapProfile.sampleElevationDeg(5f)).isWithin(0.01f).of(0.5f)
    }

    @Test
    fun orientationChange_reprojectsSameProfileToDifferentPoints() {
        val variedProfile =
            HorizonProfile(
                azimuthStepDeg = AppConfig.HORIZON_AZIMUTH_STEP_DEG,
                elevationAnglesDeg =
                FloatArray((360f / AppConfig.HORIZON_AZIMUTH_STEP_DEG).toInt()) { index ->
                    (index % 10).toFloat()
                }
            )
        val northFacing = flatten(project(variedProfile, trueAzimuthDeg = 0f, cameraElevationDeg = 0f))
        val eastFacing = flatten(project(variedProfile, trueAzimuthDeg = 90f, cameraElevationDeg = 0f))

        assertThat(northFacing).isNotEmpty()
        assertThat(eastFacing).isNotEmpty()
        assertThat(northFacing.map { it.xPx to it.yPx })
            .isNotEqualTo(eastFacing.map { it.xPx to it.yPx })
    }

    // H-P1-03: in landscape the sensor FOV axes swap; the horizontal screen span must use the sensor's
    // vertical FOV. project() end-to-end (not orientedForScreen in isolation).
    @Test
    fun landscapeScreen_usesVerticalFovAsHorizontalSpan() {
        val flatProfile =
            HorizonProfile(
                azimuthStepDeg = AppConfig.HORIZON_AZIMUTH_STEP_DEG,
                elevationAnglesDeg = FloatArray((360f / AppConfig.HORIZON_AZIMUTH_STEP_DEG).toInt()) { 0f }
            )
        val segments =
            HorizonProjector.project(
                profile = flatProfile,
                trueAzimuthDeg = 100f,
                cameraElevationDeg = 0f,
                // Sensor 60°×45°; landscape swaps → 45° becomes the horizontal span.
                fov = CameraFieldOfView(horizontalDeg = 60f, verticalDeg = 45f),
                screenWidthPx = 1920f,
                screenHeightPx = 1080f
            )
        val points = segments.flatMap { it.points }

        assertThat(points).isNotEmpty()
        assertThat(points.map { it.xPx }.minOrNull()).isWithin(1f).of(0f)
        assertThat(points.map { it.xPx }.maxOrNull()).isWithin(1f).of(1920f)
        // 45° span sampled at 1° → ~46 points (a 60° span would give ~61).
        assertThat(points.size).isAtLeast(44)
        assertThat(points.size).isAtMost(48)
        // Flat profile + level camera → every point sits on the mid-screen horizontal line.
        points.forEach { assertThat(it.yPx).isWithin(1f).of(540f) }
        // No sample lands exactly on the heading (span 45° sampled at 1° starts at -22.5°), so the
        // nearest point to center is within one screen-azimuth step: (1°/22.5°)*960 ≈ 43px.
        val centerPoint = points.minByOrNull { kotlin.math.abs(it.xPx - 960f) }!!
        assertThat(centerPoint.xPx).isWithin(43f).of(960f)
    }

    // H-P1-04: small pitch changes move the skyline smoothly. Looking further up pushes the line down
    // (mean Y increases); catches an inverted pitch sign or a wrong relativeElevation formula.
    @Test
    fun moderatePitch_movesLineDownMonotonically() {
        val flatProfile =
            HorizonProfile(
                azimuthStepDeg = AppConfig.HORIZON_AZIMUTH_STEP_DEG,
                elevationAnglesDeg = FloatArray((360f / AppConfig.HORIZON_AZIMUTH_STEP_DEG).toInt()) { 0f }
            )
        val meanYAt = { pitch: Float ->
            flatten(project(flatProfile, trueAzimuthDeg = 100f, cameraElevationDeg = pitch))
                .map { it.yPx }
                .average()
        }

        val level = meanYAt(0f)
        val pitch10 = meanYAt(10f)
        val pitch20 = meanYAt(20f)
        assertThat(pitch10).isGreaterThan(level)
        assertThat(pitch20).isGreaterThan(pitch10)
    }

    // Device regression: a cliff between neighbouring azimuths that is *fully inside* the vertical FOV.
    // Omit-on-leaving-frustum (H-DEC-1) never triggers here, so the projector used to stroke straight
    // through the step and draw a tall yellow bar. The polyline must break instead, and both terraces
    // must still be drawn.
    @Test
    fun inFovCliff_breaksPolylineInsteadOfStrokingVerticalBar() {
        val bucketCount = (360f / AppConfig.HORIZON_AZIMUTH_STEP_DEG).toInt()
        val lowDeg = 0f
        // Screen sampling is finer than the profile buckets, so a bucket-to-bucket cliff is spread over
        // ~2 screen steps: the delta has to clear roughly twice the break threshold to trigger a split.
        val highDeg = 25f
        val cliffProfile =
            HorizonProfile(
                azimuthStepDeg = AppConfig.HORIZON_AZIMUTH_STEP_DEG,
                elevationAnglesDeg =
                FloatArray(bucketCount) { index -> if (index < bucketCount / 2) lowDeg else highDeg }
            )

        val segments =
            project(
                cliffProfile,
                trueAzimuthDeg = cliffProfile.azimuthDegForIndex(bucketCount / 2),
                cameraElevationDeg = 0f,
                verticalFov = 56.2f
            )
        val maxDeltaYPx = AppConfig.HORIZON_MAX_SEGMENT_DELTA_Y_FRACTION * SCREEN_HEIGHT

        // Both terraces are in view, so the cliff has to be a break between polylines, not one stroke.
        assertThat(segments.size).isAtLeast(2)
        segments.forEach { segment ->
            segment.points.zipWithNext { a, b ->
                assertThat(kotlin.math.abs(b.yPx - a.yPx)).isAtMost(maxDeltaYPx)
            }
        }
        val allY = flatten(segments).map { it.yPx }
        val lowY = ScreenProjector.anchorYPx(lowDeg.toDouble(), 28.1f, SCREEN_HEIGHT)
        val highY = ScreenProjector.anchorYPx(highDeg.toDouble(), 28.1f, SCREEN_HEIGHT)
        assertThat(allY.minOrNull()!!).isWithin(2f).of(highY)
        assertThat(allY.maxOrNull()!!).isWithin(2f).of(lowY)
    }

    // H-P2-01: no-dependency stand-in for a drawing/screenshot test (no Roborazzi/Paparazzi in the repo).
    // NofARHorizonOutline strokes each emitted segment as a Path, so the "yellow vertical loop / full-height
    // bar" class of bugs can only reach the screen if the projector emits such a segment. Fuzzing headings
    // and pitches over a spiky profile, assert every segment advances monotonically in X (never stacks two
    // points in one screen column) and never jumps a near-full screen height between consecutive points.
    @Test
    fun spikyProfile_acrossHeadingsAndPitches_neverEmitsVerticalColumnSegment() {
        val spikyProfile =
            HorizonProfile(
                azimuthStepDeg = AppConfig.HORIZON_AZIMUTH_STEP_DEG,
                elevationAnglesDeg =
                FloatArray((360f / AppConfig.HORIZON_AZIMUTH_STEP_DEG).toInt()) { index ->
                    if (index % 2 == 0) 0f else 20f
                }
            )
        // Bound is the real rendering contract, not a loose sanity value: anything above this fraction
        // must have been broken into a separate polyline rather than stroked as a near-vertical bar.
        val maxDeltaYPx = AppConfig.HORIZON_MAX_SEGMENT_DELTA_Y_FRACTION * SCREEN_HEIGHT

        var headingDeg = 0f
        while (headingDeg < 360f) {
            for (pitchDeg in -10..30 step 10) {
                val segments =
                    project(spikyProfile, trueAzimuthDeg = headingDeg, cameraElevationDeg = pitchDeg.toFloat())
                segments.forEach { segment ->
                    segment.points.zipWithNext { a, b ->
                        assertThat(b.xPx).isGreaterThan(a.xPx)
                        assertThat(kotlin.math.abs(b.yPx - a.yPx)).isLessThan(maxDeltaYPx)
                    }
                }
            }
            headingDeg += 30f
        }
    }

    private fun project(
        profile: HorizonProfile,
        trueAzimuthDeg: Float,
        cameraElevationDeg: Float,
        horizontalFov: Float = 60f,
        verticalFov: Float = 45f
    ): List<HorizonScreenPolyline> = HorizonProjector.project(
        profile = profile,
        trueAzimuthDeg = trueAzimuthDeg,
        cameraElevationDeg = cameraElevationDeg,
        fov = CameraFieldOfView(horizontalDeg = horizontalFov, verticalDeg = verticalFov),
        screenWidthPx = SCREEN_WIDTH,
        screenHeightPx = SCREEN_HEIGHT
    )

    private fun flatten(segments: List<HorizonScreenPolyline>): List<HorizonScreenPoint> =
        segments.flatMap { it.points }

    private companion object {
        const val SCREEN_WIDTH = 1080f
        const val SCREEN_HEIGHT = 1920f
    }
}
