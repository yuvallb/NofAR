package com.nofar.core.visibility

import com.nofar.core.model.AppConfig
import kotlin.math.floor

data class HorizonScreenPoint(val xPx: Float, val yPx: Float)

/**
 * Maps a cached [HorizonProfile] to screen coordinates on the high-frequency Explore render path.
 * No I/O — safe for orientation-driven reprojection.
 */
object HorizonProjector {
    fun project(
        profile: HorizonProfile,
        trueAzimuthDeg: Float,
        cameraElevationDeg: Float,
        fov: CameraFieldOfView,
        screenWidthPx: Float,
        screenHeightPx: Float
    ): List<HorizonScreenPoint> {
        if (screenWidthPx <= 0f || screenHeightPx <= 0f || profile.elevationAnglesDeg.isEmpty()) {
            return emptyList()
        }

        val orientedFov = fov.orientedForScreen(screenWidthPx, screenHeightPx)
        val halfHorizontalFov = orientedFov.horizontalDeg / 2f
        val halfVerticalFov = orientedFov.verticalDeg / 2f
        val paddingDeg = AppConfig.HORIZON_SCREEN_PADDING_DEG
        val minAzimuth = trueAzimuthDeg - halfHorizontalFov - paddingDeg
        val maxAzimuth = trueAzimuthDeg + halfHorizontalFov + paddingDeg

        return azimuthSamples(
            minAzimuthDeg = minAzimuth,
            maxAzimuthDeg = maxAzimuth,
            stepDeg = profile.azimuthStepDeg
        ).map { azimuthDeg ->
            val bucketIndex = azimuthToBucketIndex(azimuthDeg, profile.azimuthStepDeg, profile.elevationAnglesDeg.size)
            val bearingDeg = normalizeAzimuthDeg(azimuthDeg).toDouble()
            val elevationAngleDeg = profile.elevationAnglesDeg[bucketIndex].toDouble()
            val headingDelta = ScreenProjector.normalizeHeadingDelta(bearingDeg, trueAzimuthDeg)
            val relativeElevation = elevationAngleDeg - cameraElevationDeg.toDouble()
            HorizonScreenPoint(
                xPx =
                ScreenProjector.anchorXPx(
                    headingDeltaDeg = headingDelta,
                    halfHorizontalFovDeg = halfHorizontalFov,
                    screenWidthPx = screenWidthPx
                ),
                yPx =
                ScreenProjector.anchorYPx(
                    relativeElevationDeg = relativeElevation,
                    halfVerticalFovDeg = halfVerticalFov,
                    screenHeightPx = screenHeightPx
                )
            )
        }
    }

    internal fun azimuthSamples(minAzimuthDeg: Float, maxAzimuthDeg: Float, stepDeg: Float): List<Float> {
        if (stepDeg <= 0f || minAzimuthDeg > maxAzimuthDeg) return emptyList()

        val samples = mutableListOf<Float>()
        var azimuth = minAzimuthDeg
        while (azimuth <= maxAzimuthDeg + stepDeg * 0.01f) {
            samples += azimuth
            azimuth += stepDeg
        }
        return samples
    }

    internal fun normalizeAzimuthDeg(azimuthDeg: Float): Float {
        var normalized = azimuthDeg % 360f
        if (normalized < 0f) normalized += 360f
        return normalized
    }

    private fun azimuthToBucketIndex(azimuthDeg: Float, azimuthStepDeg: Float, bucketCount: Int): Int {
        val normalized = normalizeAzimuthDeg(azimuthDeg)
        val rawIndex = floor(normalized / azimuthStepDeg).toInt()
        return rawIndex.coerceIn(0, bucketCount - 1)
    }
}
