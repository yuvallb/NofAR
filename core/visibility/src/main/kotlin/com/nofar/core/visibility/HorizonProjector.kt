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
        val bucketCount = profile.elevationAnglesDeg.size
        val startIndex = azimuthToBucketIndex(minAzimuth, profile.azimuthStepDeg, bucketCount)
        val endIndex = azimuthToBucketIndex(maxAzimuth, profile.azimuthStepDeg, bucketCount)
        val indices = bucketIndexRange(startIndex, endIndex, bucketCount)

        return indices.mapNotNull { bucketIndex ->
            val bearingDeg = profile.azimuthDegForIndex(bucketIndex).toDouble()
            val elevationAngleDeg = profile.elevationAnglesDeg[bucketIndex].toDouble()
            val headingDelta = ScreenProjector.normalizeHeadingDelta(bearingDeg, trueAzimuthDeg)
            val relativeElevation = elevationAngleDeg - cameraElevationDeg.toDouble()
            val anchorXPx =
                ScreenProjector.anchorXPx(
                    headingDeltaDeg = headingDelta,
                    halfHorizontalFovDeg = halfHorizontalFov,
                    screenWidthPx = screenWidthPx
                )
            val anchorYPx =
                ScreenProjector.anchorYPx(
                    relativeElevationDeg = relativeElevation,
                    halfVerticalFovDeg = halfVerticalFov,
                    screenHeightPx = screenHeightPx
                )
            HorizonScreenPoint(xPx = anchorXPx, yPx = anchorYPx)
        }
    }

    private fun azimuthToBucketIndex(azimuthDeg: Float, azimuthStepDeg: Float, bucketCount: Int): Int {
        var normalized = azimuthDeg % 360f
        if (normalized < 0f) normalized += 360f
        val rawIndex = floor(normalized / azimuthStepDeg).toInt()
        return rawIndex.coerceIn(0, bucketCount - 1)
    }

    private fun bucketIndexRange(startIndex: Int, endIndex: Int, bucketCount: Int): List<Int> =
        if (startIndex <= endIndex) {
            (startIndex..endIndex).toList()
        } else {
            (startIndex until bucketCount).toList() + (0..endIndex).toList()
        }
}
