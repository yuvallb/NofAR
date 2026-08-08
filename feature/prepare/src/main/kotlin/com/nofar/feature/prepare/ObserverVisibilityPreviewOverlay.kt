package com.nofar.feature.prepare

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

internal class ObserverVisibilityPreviewOverlay : Overlay() {
    var mask: MapVisibilityPreviewMask? = null

    private val maskPaint =
        Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val current = mask ?: return
        drawGeoreferencedMask(canvas, mapView, current.bitmap, current.bounds)
    }

    private fun drawGeoreferencedMask(
        canvas: Canvas,
        mapView: MapView,
        bitmap: Bitmap,
        bounds: MapVisibilityPreviewMaskBounds
    ) {
        val projection = mapView.projection
        val halfM = bounds.halfExtentM
        val diagonalM = halfM * kotlin.math.sqrt(2.0)
        val topLeft = destinationPoint(bounds.observerLat, bounds.observerLon, diagonalM, 315.0)
        val topRight = destinationPoint(bounds.observerLat, bounds.observerLon, diagonalM, 45.0)
        val bottomLeft = destinationPoint(bounds.observerLat, bounds.observerLon, diagonalM, 225.0)
        val bottomRight = destinationPoint(bounds.observerLat, bounds.observerLon, diagonalM, 135.0)
        val tl = projection.toPixels(GeoPoint(topLeft.first, topLeft.second), null)
        val tr = projection.toPixels(GeoPoint(topRight.first, topRight.second), null)
        val bl = projection.toPixels(GeoPoint(bottomLeft.first, bottomLeft.second), null)
        val br = projection.toPixels(GeoPoint(bottomRight.first, bottomRight.second), null)
        val matrix = Matrix()
        val src =
            floatArrayOf(
                0f,
                0f,
                bitmap.width.toFloat(),
                0f,
                bitmap.width.toFloat(),
                bitmap.height.toFloat(),
                0f,
                bitmap.height.toFloat()
            )
        val dst =
            floatArrayOf(
                tl.x.toFloat(),
                tl.y.toFloat(),
                tr.x.toFloat(),
                tr.y.toFloat(),
                br.x.toFloat(),
                br.y.toFloat(),
                bl.x.toFloat(),
                bl.y.toFloat()
            )
        matrix.setPolyToPoly(src, 0, dst, 0, 4)
        canvas.save()
        canvas.concat(matrix)
        canvas.drawBitmap(bitmap, 0f, 0f, maskPaint)
        canvas.restore()
    }
}
