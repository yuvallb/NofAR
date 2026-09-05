package com.nofar.core.visibility

import com.nofar.core.model.AppConfig
import kotlin.math.abs

data class HorizonScreenPoint(val xPx: Float, val yPx: Float)

data class HorizonScreenPolyline(val points: List<HorizonScreenPoint>)

/**
 * Maps a cached [HorizonProfile] to screen coordinates on the high-frequency Explore render path.
 * No I/O — safe for orientation-driven reprojection.
 */
object HorizonProjector {
    private val SCREEN_AZIMUTH_STEP_DEG = AppConfig.HORIZON_SCREEN_AZIMUTH_STEP_DEG

    fun project(
        profile: HorizonProfile,
        trueAzimuthDeg: Float,
        cameraElevationDeg: Float,
        fov: CameraFieldOfView,
        screenWidthPx: Float,
        screenHeightPx: Float
    ): List<HorizonScreenPolyline> {
        val orientedFov = fov.orientedForScreen(screenWidthPx, screenHeightPx)
        val halfHorizontalFov = orientedFov.horizontalDeg / 2f
        val halfVerticalFov = orientedFov.verticalDeg / 2f
        if (screenWidthPx <= 0f || screenHeightPx <= 0f || profile.elevationAnglesDeg.isEmpty()) {
            return emptyList()
        }

        return if (halfHorizontalFov <= 0f || halfVerticalFov <= 0f) {
            emptyList()
        } else {
            buildProjectedSegments(
                profile = profile,
                trueAzimuthDeg = trueAzimuthDeg,
                cameraElevationDeg = cameraElevationDeg,
                halfHorizontalFov = halfHorizontalFov,
                halfVerticalFov = halfVerticalFov,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx
            )
        }
    }

    private fun buildProjectedSegments(
        profile: HorizonProfile,
        trueAzimuthDeg: Float,
        cameraElevationDeg: Float,
        halfHorizontalFov: Float,
        halfVerticalFov: Float,
        screenWidthPx: Float,
        screenHeightPx: Float
    ): List<HorizonScreenPolyline> {
        val segments = mutableListOf<HorizonScreenPolyline>()
        var currentSegment = mutableListOf<HorizonScreenPoint>()
        val halfVerticalFovDeg = halfVerticalFov.toDouble()
        val maxSegmentDeltaYPx = AppConfig.HORIZON_MAX_SEGMENT_DELTA_Y_FRACTION * screenHeightPx
        var headingDelta = -halfHorizontalFov
        while (headingDelta <= halfHorizontalFov + SCREEN_AZIMUTH_STEP_DEG * 0.01f) {
            val bearingDeg = normalizeAzimuthDeg(trueAzimuthDeg + headingDelta)
            val elevationAngleDeg = profile.sampleElevationDeg(bearingDeg).toDouble()
            if (elevationAngleDeg.isNaN()) {
                appendSegment(segments, currentSegment)
                currentSegment = mutableListOf()
                headingDelta += SCREEN_AZIMUTH_STEP_DEG
                continue
            }
            val relativeElevation = elevationAngleDeg - cameraElevationDeg.toDouble()
            if (abs(relativeElevation) > halfVerticalFovDeg) {
                // H-DEC-1 Option A: the skyline is outside the vertical frustum here. Omit the point and
                // break the polyline instead of clamping it to a screen edge — clamping drew a fake flat
                // line pinned to the top/bottom of the screen.
                appendSegment(segments, currentSegment)
                currentSegment = mutableListOf()
            } else {
                val point =
                    HorizonScreenPoint(
                        xPx =
                        ScreenProjector.anchorXPx(
                            headingDeltaDeg = headingDelta.toDouble(),
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
                val previous = currentSegment.lastOrNull()
                if (previous != null && abs(point.yPx - previous.yPx) > maxSegmentDeltaYPx) {
                    // Too steep to be a stroke: start a fresh polyline at this point so nothing is drawn
                    // across the jump. See AppConfig.HORIZON_MAX_SEGMENT_DELTA_Y_FRACTION.
                    appendSegment(segments, currentSegment)
                    currentSegment = mutableListOf()
                }
                currentSegment += point
            }
            headingDelta += SCREEN_AZIMUTH_STEP_DEG
        }
        appendSegment(segments, currentSegment)
        return segments
    }

    private fun appendSegment(segments: MutableList<HorizonScreenPolyline>, points: MutableList<HorizonScreenPoint>) {
        if (points.size >= 2) {
            segments += HorizonScreenPolyline(points.toList())
        }
        points.clear()
    }

    internal fun normalizeAzimuthDeg(azimuthDeg: Float): Float {
        var normalized = azimuthDeg % 360f
        if (normalized < 0f) normalized += 360f
        return normalized
    }
}
