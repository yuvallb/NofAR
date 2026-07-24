package com.nofar.core.visibility

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.min

data class CameraFieldOfView(val horizontalDeg: Float, val verticalDeg: Float, val isFallback: Boolean = false) {
    /**
     * Maps sensor-space horizontal/vertical FOV to the current screen axes.
     * In landscape the camera sensor axes swap relative to screen width/height.
     */
    fun orientedForScreen(screenWidthPx: Float, screenHeightPx: Float): CameraFieldOfView {
        if (screenWidthPx <= screenHeightPx) return this
        return copy(horizontalDeg = verticalDeg, verticalDeg = horizontalDeg)
    }

    companion object {
        fun fallback(): CameraFieldOfView = CameraFieldOfView(
            horizontalDeg = com.nofar.core.model.AppConfig.CAMERA_HORIZONTAL_FOV_FALLBACK_DEG,
            verticalDeg = com.nofar.core.model.AppConfig.CAMERA_VERTICAL_FOV_FALLBACK_DEG,
            isFallback = true
        )
    }
}

data class ScreenPoint(val anchorXPx: Float, val anchorYPx: Float, val headingDeltaDeg: Double)

data class FootprintScreenSpan(
    val leftXPx: Float,
    val rightXPx: Float,
    val anchorXPx: Float,
    val angularDiameterDeg: Double
)

/**
 * Pure screen projection for Explore AR labels (Requirements §3.3.2).
 * No I/O, no suspend — safe for the high-frequency render path.
 */
object ScreenProjector {
    fun normalizeHeadingDelta(bearingDeg: Double, trueAzimuthDeg: Float): Double {
        var delta = bearingDeg - trueAzimuthDeg.toDouble()
        while (delta > 180.0) delta -= 360.0
        while (delta < -180.0) delta += 360.0
        return delta
    }

    fun projectEntityToScreen(
        bearingDeg: Double,
        elevationAngleDeg: Double,
        trueAzimuthDeg: Float,
        cameraElevationDeg: Float,
        horizontalFovDeg: Float,
        verticalFovDeg: Float,
        screenWidthPx: Float,
        screenHeightPx: Float
    ): ScreenPoint? {
        if (screenWidthPx <= 0f || screenHeightPx <= 0f) return null

        val headingDelta = normalizeHeadingDelta(bearingDeg, trueAzimuthDeg)
        val halfHorizontalFov = horizontalFovDeg / 2f
        val halfVerticalFov = verticalFovDeg / 2f
        val relativeElevation = elevationAngleDeg - cameraElevationDeg.toDouble()
        val inView =
            abs(headingDelta) <= halfHorizontalFov &&
                abs(relativeElevation) <= halfVerticalFov

        return if (!inView) {
            null
        } else {
            ScreenPoint(
                anchorXPx =
                anchorXPx(
                    headingDeltaDeg = headingDelta,
                    halfHorizontalFovDeg = halfHorizontalFov,
                    screenWidthPx = screenWidthPx
                ),
                anchorYPx =
                anchorYPx(
                    relativeElevationDeg = relativeElevation,
                    halfVerticalFovDeg = halfVerticalFov,
                    screenHeightPx = screenHeightPx
                ),
                headingDeltaDeg = headingDelta
            )
        }
    }

    fun anchorXPx(headingDeltaDeg: Double, halfHorizontalFovDeg: Float, screenWidthPx: Float): Float =
        screenWidthPx / 2f +
            (headingDeltaDeg / halfHorizontalFovDeg).toFloat() * (screenWidthPx / 2f)

    fun anchorYPx(relativeElevationDeg: Double, halfVerticalFovDeg: Float, screenHeightPx: Float): Float =
        screenHeightPx / 2f -
            (relativeElevationDeg / halfVerticalFovDeg).toFloat() * (screenHeightPx / 2f)

    fun projectFootprintSpan(
        bearingDeg: Double,
        footprintRadiusM: Double,
        centerDistanceM: Double,
        trueAzimuthDeg: Float,
        horizontalFovDeg: Float,
        screenWidthPx: Float
    ): FootprintScreenSpan? {
        if (screenWidthPx <= 0f || footprintRadiusM <= 0.0) return null

        val halfHorizontalFov = horizontalFovDeg / 2f
        val angularHalfWidthDeg =
            if (centerDistanceM <= footprintRadiusM) {
                90.0
            } else {
                val ratio = (footprintRadiusM / centerDistanceM).coerceIn(-1.0, 1.0)
                Math.toDegrees(asin(ratio))
            }
        val headingDelta = normalizeHeadingDelta(bearingDeg, trueAzimuthDeg)
        val leftDelta = headingDelta - angularHalfWidthDeg
        val rightDelta = headingDelta + angularHalfWidthDeg
        val inHorizontalView = rightDelta >= -halfHorizontalFov && leftDelta <= halfHorizontalFov
        return if (!inHorizontalView) {
            null
        } else {
            val clippedLeft = leftDelta.coerceIn(-halfHorizontalFov.toDouble(), halfHorizontalFov.toDouble())
            val clippedRight = rightDelta.coerceIn(-halfHorizontalFov.toDouble(), halfHorizontalFov.toDouble())
            val leftXPx = anchorXPx(clippedLeft, halfHorizontalFov, screenWidthPx)
            val rightXPx = anchorXPx(clippedRight, halfHorizontalFov, screenWidthPx)
            val midpointDelta = (clippedLeft + clippedRight) / 2.0
            val spanAnchorXPx = anchorXPx(midpointDelta, halfHorizontalFov, screenWidthPx)
            val angularDiameterDeg = min(clippedRight - clippedLeft, angularHalfWidthDeg * 2.0)
            FootprintScreenSpan(
                leftXPx = leftXPx,
                rightXPx = rightXPx,
                anchorXPx = spanAnchorXPx,
                angularDiameterDeg = angularDiameterDeg
            )
        }
    }

    fun elevationToScreenY(
        elevationAngleDeg: Double,
        cameraElevationDeg: Float,
        verticalFovDeg: Float,
        screenHeightPx: Float
    ): Float? {
        if (screenHeightPx <= 0f) return null
        val halfVerticalFov = verticalFovDeg / 2f
        val relativeElevation = elevationAngleDeg - cameraElevationDeg.toDouble()
        return if (abs(relativeElevation) > halfVerticalFov) {
            null
        } else {
            anchorYPx(relativeElevation, halfVerticalFov, screenHeightPx)
        }
    }
}
