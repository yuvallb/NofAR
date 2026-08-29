package com.nofar.core.visibility

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.AppConfig
import org.junit.Test

class HorizonSkylineExtractorTest {
    @Test
    fun extractNormalizedProfile_detectsHorizontalEdge() {
        val width = 160
        val height = 120
        val horizonRow = 48
        val yPlane = ByteArray(width * height) { index ->
            val row = index / width
            if (row < horizonRow) 220.toByte() else 40.toByte()
        }

        val profile = HorizonSkylineExtractor.extractNormalizedProfile(yPlane, width, height, columnCount = 8)

        profile.forEach { y ->
            assertThat(y).isFinite()
            assertThat(y).isWithin(0.05f).of(horizonRow.toFloat() / (height - 1))
        }
    }

    @Test
    fun extractNormalizedProfile_lowContrastColumn_isNaN() {
        val width = 80
        val height = 60
        val yPlane = ByteArray(width * height) { 100.toByte() }

        val profile = HorizonSkylineExtractor.extractNormalizedProfile(yPlane, width, height, columnCount = 4)

        profile.forEach { y -> assertThat(y.isNaN()).isTrue() }
    }
}

class HorizonAlignmentMatcherTest {
    private val screenWidth = 1080f
    private val screenHeight = 1920f
    private val fov = CameraFieldOfView(horizontalDeg = 60f, verticalDeg = 45f)

    @Test
    fun match_recoversSmallShift() {
        val horizonProfile = peakedProfile()
        val demAtZero =
            HorizonAlignmentMatcher.sampleDemProfileYNormalized(
                horizonProfile = horizonProfile,
                trueAzimuthDeg = 100f,
                cameraElevationDeg = 2f,
                azimuthOffsetDeg = 0f,
                pitchOffsetDeg = 0f,
                fov = fov,
                screenWidthPx = screenWidth,
                screenHeightPx = screenHeight,
                columnCount = AppConfig.HORIZON_ALIGN_PROFILE_COLUMNS
            )
        val cameraProfile =
            HorizonAlignmentMatcher.sampleDemProfileYNormalized(
                horizonProfile = horizonProfile,
                trueAzimuthDeg = 100f,
                cameraElevationDeg = 2f,
                azimuthOffsetDeg = 3f,
                pitchOffsetDeg = -1f,
                fov = fov,
                screenWidthPx = screenWidth,
                screenHeightPx = screenHeight,
                columnCount = AppConfig.HORIZON_ALIGN_PROFILE_COLUMNS
            )

        val result =
            HorizonAlignmentMatcher.match(
                cameraProfileYNormalized = cameraProfile,
                horizonProfile = horizonProfile,
                trueAzimuthDeg = 100f,
                cameraElevationDeg = 2f,
                fov = fov,
                screenWidthPx = screenWidth,
                screenHeightPx = screenHeight
            )

        assertThat(result.accepted).isTrue()
        assertThat(result.azimuthOffsetDeg).isWithin(0.75f).of(3f)
        assertThat(result.pitchOffsetDeg).isWithin(0.75f).of(-1f)
        assertThat(HorizonSkylineExtractor.normalizedVariance(demAtZero)).isGreaterThan(0f)
    }

    @Test
    fun match_largeShift_isRejectedOverThreshold() {
        val horizonProfile = peakedProfile()
        val cameraProfile =
            HorizonAlignmentMatcher.sampleDemProfileYNormalized(
                horizonProfile = horizonProfile,
                trueAzimuthDeg = 100f,
                cameraElevationDeg = 0f,
                azimuthOffsetDeg = 15f,
                pitchOffsetDeg = 0f,
                fov = fov,
                screenWidthPx = screenWidth,
                screenHeightPx = screenHeight,
                columnCount = AppConfig.HORIZON_ALIGN_PROFILE_COLUMNS
            )

        val result =
            HorizonAlignmentMatcher.match(
                cameraProfileYNormalized = cameraProfile,
                horizonProfile = horizonProfile,
                trueAzimuthDeg = 100f,
                cameraElevationDeg = 0f,
                fov = fov,
                screenWidthPx = screenWidth,
                screenHeightPx = screenHeight
            )

        assertThat(result.accepted).isFalse()
        assertThat(result.rejectReason).isEqualTo(HorizonAlignmentRejectReason.OVER_THRESHOLD)
    }

    @Test
    fun match_flatProfile_isRejected() {
        val flatProfile =
            HorizonProfile(
                azimuthStepDeg = AppConfig.HORIZON_AZIMUTH_STEP_DEG,
                elevationAnglesDeg = FloatArray((360f / AppConfig.HORIZON_AZIMUTH_STEP_DEG).toInt()) { 0f }
            )
        val cameraProfile =
            FloatArray(AppConfig.HORIZON_ALIGN_PROFILE_COLUMNS) { 0.5f }

        val result =
            HorizonAlignmentMatcher.match(
                cameraProfileYNormalized = cameraProfile,
                horizonProfile = flatProfile,
                trueAzimuthDeg = 0f,
                cameraElevationDeg = 0f,
                fov = fov,
                screenWidthPx = screenWidth,
                screenHeightPx = screenHeight
            )

        assertThat(result.accepted).isFalse()
        assertThat(result.rejectReason).isEqualTo(HorizonAlignmentRejectReason.FLAT_SKYLINE)
    }

    private fun peakedProfile(): HorizonProfile {
        val bucketCount = (360f / AppConfig.HORIZON_AZIMUTH_STEP_DEG).toInt()
        return HorizonProfile(
            azimuthStepDeg = AppConfig.HORIZON_AZIMUTH_STEP_DEG,
            elevationAnglesDeg =
            FloatArray(bucketCount) { index ->
                val center = bucketCount / 2
                val distance = kotlin.math.abs(index - center).coerceAtMost(center)
                12f - distance * 0.8f
            }
        )
    }
}

class FillCenterCropTest {
    @Test
    fun cropYPlane_portraitView_cropsHorizontalSides() {
        val width = 320
        val height = 240
        val yPlane = ByteArray(width * height) { (it % 256).toByte() }
        val viewAspect = 1080f / 1920f

        val cropped = FillCenterCrop.cropYPlane(yPlane, width, height, viewAspect)

        assertThat(cropped).isNotNull()
        assertThat(cropped!!.width).isLessThan(width)
        assertThat(cropped.height).isEqualTo(height)
    }
}
