package com.nofar.core.visibility

import kotlin.math.abs

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
                screenWidthPx / 2f +
                    (headingDelta / halfHorizontalFov).toFloat() * (screenWidthPx / 2f),
                anchorYPx =
                screenHeightPx / 2f -
                    (relativeElevation / halfVerticalFov).toFloat() * (screenHeightPx / 2f),
                headingDeltaDeg = headingDelta
            )
        }
    }
}
