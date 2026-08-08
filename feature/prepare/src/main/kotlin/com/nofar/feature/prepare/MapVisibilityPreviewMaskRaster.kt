package com.nofar.feature.prepare

import android.graphics.Bitmap
import android.graphics.Color
import com.nofar.core.model.AppConfig
import com.nofar.core.visibility.MapVisibilityCellState
import com.nofar.core.visibility.MapVisibilityPreview
import kotlin.math.atan2
import kotlin.math.hypot

/** Square mask centered on [MapVisibilityPreview.observerLat]/[MapVisibilityPreview.observerLon]. */
data class MapVisibilityPreviewMaskBounds(val observerLat: Double, val observerLon: Double, val halfExtentM: Double)

internal object MapVisibilityPreviewMaskRaster {
    private const val VISIBLE_COLOR = 0x4D4CAF50
    private const val BLOCKED_COLOR = 0x4DF44336
    private const val UNKNOWN_NEUTRAL = 0x33888888

    fun rasterize(
        preview: MapVisibilityPreview,
        sizePx: Int = AppConfig.MAP_PREVIEW_MASK_SIZE_PX
    ): Pair<Bitmap, MapVisibilityPreviewMaskBounds> {
        val halfExtentM = preview.maxRegionEdgeM().coerceAtLeast(AppConfig.MAP_PREVIEW_RADIAL_STEP_M)
        val bounds =
            MapVisibilityPreviewMaskBounds(
                observerLat = preview.observerLat,
                observerLon = preview.observerLon,
                halfExtentM = halfExtentM
            )
        val metersPerPixel = (2.0 * halfExtentM) / sizePx
        val pixels = IntArray(sizePx * sizePx)
        val center = sizePx / 2.0
        for (y in 0 until sizePx) {
            for (x in 0 until sizePx) {
                val eastM = (x - center + 0.5) * metersPerPixel
                val northM = (center - y - 0.5) * metersPerPixel
                val distanceM = hypot(eastM, northM)
                var azimuthDeg = Math.toDegrees(atan2(eastM, northM))
                if (azimuthDeg < 0.0) azimuthDeg += 360.0
                pixels[y * sizePx + x] =
                    pixelColor(
                        preview = preview,
                        azimuthDeg = azimuthDeg,
                        distanceM = distanceM,
                        halfExtentM = halfExtentM
                    )
            }
        }
        featherEdges(pixels, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        return bitmap to bounds
    }

    private fun pixelColor(
        preview: MapVisibilityPreview,
        azimuthDeg: Double,
        distanceM: Double,
        halfExtentM: Double
    ): Int {
        val insideMask =
            distanceM <= halfExtentM &&
                preview.isInsideRegion(azimuthDeg, distanceM)
        if (!insideMask) return Color.TRANSPARENT
        return when (preview.sampleState(azimuthDeg, distanceM)) {
            MapVisibilityCellState.VISIBLE -> VISIBLE_COLOR
            MapVisibilityCellState.BLOCKED -> BLOCKED_COLOR
            MapVisibilityCellState.UNKNOWN -> UNKNOWN_NEUTRAL
        }
    }

    private fun featherEdges(pixels: IntArray, sizePx: Int) {
        val copy = pixels.copyOf()
        for (y in 1 until sizePx - 1) {
            for (x in 1 until sizePx - 1) {
                val center = copy[y * sizePx + x]
                if (center == Color.TRANSPARENT) continue
                val neighbors =
                    listOf(
                        copy[(y - 1) * sizePx + x],
                        copy[(y + 1) * sizePx + x],
                        copy[y * sizePx + (x - 1)],
                        copy[y * sizePx + (x + 1)]
                    )
                if (neighbors.any { it != center && it != Color.TRANSPARENT }) {
                    pixels[y * sizePx + x] =
                        Color.argb(
                            Color.alpha(center) * 3 / 4,
                            Color.red(center),
                            Color.green(center),
                            Color.blue(center)
                        )
                }
            }
        }
    }
}
