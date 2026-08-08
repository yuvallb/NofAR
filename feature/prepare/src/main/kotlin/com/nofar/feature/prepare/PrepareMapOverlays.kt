@file:Suppress("MaxLineLength")

package com.nofar.feature.prepare

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.nofar.core.model.Region
import java.util.UUID
import kotlin.math.hypot
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay

@Composable
internal fun PrepareRegionMap(
    centerLat: Double,
    centerLon: Double,
    radiusKm: Double,
    downloadedRegions: List<Region>,
    excludeRegionId: UUID?,
    mapRecenterNonce: Long,
    onMapTap: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    PrepareMapCore(
        centerLat = centerLat,
        centerLon = centerLon,
        downloadedRegions = downloadedRegions,
        excludeRegionId = excludeRegionId,
        mapRecenterNonce = mapRecenterNonce,
        onMapTap = onMapTap,
        modifier = modifier,
        radiusM = radiusKm * 1000
    )
}

@Composable
internal fun PreparePointPickerMap(
    selectedLat: Double,
    selectedLon: Double,
    downloadedRegions: List<Region>,
    mapRecenterNonce: Long,
    onMapTap: (Double, Double) -> Unit,
    visibilityMask: MapVisibilityPreviewMask? = null,
    modifier: Modifier = Modifier
) {
    PrepareMapCore(
        centerLat = selectedLat,
        centerLon = selectedLon,
        downloadedRegions = downloadedRegions,
        excludeRegionId = null,
        mapRecenterNonce = mapRecenterNonce,
        onMapTap = onMapTap,
        modifier = modifier,
        radiusM = null,
        visibilityMask = visibilityMask
    )
}

@Composable
private fun PrepareMapCore(
    centerLat: Double,
    centerLon: Double,
    downloadedRegions: List<Region>,
    excludeRegionId: UUID?,
    mapRecenterNonce: Long,
    onMapTap: (Double, Double) -> Unit,
    radiusM: Double?,
    visibilityMask: MapVisibilityPreviewMask? = null,
    modifier: Modifier = Modifier
) {
    var lastRecenterNonce by remember { mutableStateOf(0L) }
    val mapHolder = remember { PrepareMapHolder() }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            createPrepareMapView(
                context = context,
                centerLat = centerLat,
                centerLon = centerLon,
                radiusM = radiusM,
                onMapTap = onMapTap,
                mapHolder = mapHolder
            )
        },
        update = { mapView ->
            updatePrepareMapView(
                mapView = mapView,
                mapHolder = mapHolder,
                centerLat = centerLat,
                centerLon = centerLon,
                radiusM = radiusM,
                downloadedRegions = downloadedRegions,
                excludeRegionId = excludeRegionId,
                visibilityMask = visibilityMask,
                mapRecenterNonce = mapRecenterNonce,
                lastRecenterNonce = lastRecenterNonce,
                onRecenterApplied = { lastRecenterNonce = it }
            )
        }
    )
}

private fun createPrepareMapView(
    context: Context,
    centerLat: Double,
    centerLon: Double,
    radiusM: Double?,
    onMapTap: (Double, Double) -> Unit,
    mapHolder: PrepareMapHolder
): MapView = MapView(context).apply {
    setMultiTouchControls(true)
    controller.setZoom(10.0)
    controller.setCenter(GeoPoint(centerLat, centerLon))
    val downloadedOverlay = DownloadedRegionsOverlay()
    val circleOverlay =
        radiusM?.let { RadiusCircleOverlay(centerLat, centerLon, it) }
    val marker =
        Marker(this).apply {
            position = GeoPoint(centerLat, centerLon)
            isDraggable = false
        }
    val tapOverlay =
        TapOverlay { lat, lon ->
            onMapTap(lat, lon)
            true
        }
    val visibilityOverlay = ObserverVisibilityPreviewOverlay()
    overlays.add(downloadedOverlay)
    overlays.add(visibilityOverlay)
    circleOverlay?.let { overlays.add(it) }
    overlays.add(marker)
    overlays.add(tapOverlay)
    addMapListener(
        object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                invalidate()
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                invalidate()
                return false
            }
        }
    )
    mapHolder.downloadedOverlay = downloadedOverlay
    mapHolder.circleOverlay = circleOverlay
    mapHolder.marker = marker
    mapHolder.visibilityOverlay = visibilityOverlay
    mapHolder.mapView = this
}

private fun updatePrepareMapView(
    mapView: MapView,
    mapHolder: PrepareMapHolder,
    centerLat: Double,
    centerLon: Double,
    radiusM: Double?,
    downloadedRegions: List<Region>,
    excludeRegionId: UUID?,
    visibilityMask: MapVisibilityPreviewMask?,
    mapRecenterNonce: Long,
    lastRecenterNonce: Long,
    onRecenterApplied: (Long) -> Unit
) {
    mapHolder.marker?.position = GeoPoint(centerLat, centerLon)
    mapHolder.circleOverlay?.apply {
        this.centerLat = centerLat
        this.centerLon = centerLon
        if (radiusM != null) {
            this.radiusM = radiusM
        }
    }
    mapHolder.downloadedOverlay?.apply {
        regions = downloadedRegions.filter { excludeRegionId == null || it.id != excludeRegionId }
        showFill = visibilityMask == null
    }
    mapHolder.visibilityOverlay?.apply {
        mask = visibilityMask
    }
    if (mapRecenterNonce != lastRecenterNonce) {
        mapView.controller.animateTo(GeoPoint(centerLat, centerLon))
        onRecenterApplied(mapRecenterNonce)
    }
    mapView.invalidate()
}

private class PrepareMapHolder {
    var mapView: MapView? = null
    var marker: Marker? = null
    var circleOverlay: RadiusCircleOverlay? = null
    var downloadedOverlay: DownloadedRegionsOverlay? = null
    var visibilityOverlay: ObserverVisibilityPreviewOverlay? = null
}

internal class DownloadedRegionsOverlay : Overlay() {
    var regions: List<Region> = emptyList()
    var showFill: Boolean = true

    private val fillPaint =
        Paint().apply {
            color = 0x334CAF50
            style = Paint.Style.FILL
            isAntiAlias = true
        }
    private val outlinePaint =
        Paint().apply {
            color = 0xFF2E7D32.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
    private val labelPaint =
        Paint().apply {
            color = 0xFF1A1A1A.toInt()
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
    private val labelBgPaint =
        Paint().apply {
            color = 0xCCFFFFFF.toInt()
            style = Paint.Style.FILL
            isAntiAlias = true
        }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        regions.forEach { region ->
            drawRegionCircle(canvas, mapView, region.centerLat, region.centerLon, region.radiusM)
            drawRegionLabel(canvas, mapView, region.centerLat, region.centerLon, region.name)
        }
    }

    private fun drawRegionCircle(
        canvas: Canvas,
        mapView: MapView,
        centerLat: Double,
        centerLon: Double,
        radiusM: Double
    ) {
        val projection = mapView.projection
        val centerPoint = projection.toPixels(GeoPoint(centerLat, centerLon), null)
        val radiusPx = circleRadiusPx(mapView, centerLat, centerLon, radiusM, centerPoint.x, centerPoint.y)
        if (showFill) {
            canvas.drawCircle(centerPoint.x.toFloat(), centerPoint.y.toFloat(), radiusPx.toFloat(), fillPaint)
        }
        canvas.drawCircle(centerPoint.x.toFloat(), centerPoint.y.toFloat(), radiusPx.toFloat(), outlinePaint)
    }

    private fun drawRegionLabel(canvas: Canvas, mapView: MapView, centerLat: Double, centerLon: Double, name: String) {
        if (name.isBlank()) return
        val projection = mapView.projection
        val centerPoint = projection.toPixels(GeoPoint(centerLat, centerLon), null)
        val label = if (name.length > 24) name.take(23) + "..." else name
        val textWidth = labelPaint.measureText(label)
        val pad = 8f
        val baseline = centerPoint.y.toFloat()
        canvas.drawRect(
            centerPoint.x - textWidth / 2 - pad,
            baseline - labelPaint.textSize - pad / 2,
            centerPoint.x + textWidth / 2 + pad,
            baseline + pad,
            labelBgPaint
        )
        canvas.drawText(label, centerPoint.x.toFloat(), baseline, labelPaint)
    }
}

internal class RadiusCircleOverlay(var centerLat: Double, var centerLon: Double, var radiusM: Double) : Overlay() {
    private val fillPaint =
        Paint().apply {
            color = 0x33FFE838
            style = Paint.Style.FILL
            isAntiAlias = true
        }
    private val outlinePaint =
        Paint().apply {
            color = 0xFFFFE838.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = mapView.projection
        val centerPoint = projection.toPixels(GeoPoint(centerLat, centerLon), null)
        val radiusPx = circleRadiusPx(mapView, centerLat, centerLon, radiusM, centerPoint.x, centerPoint.y)
        canvas.drawCircle(
            centerPoint.x.toFloat(),
            centerPoint.y.toFloat(),
            radiusPx.toFloat(),
            fillPaint
        )
        canvas.drawCircle(
            centerPoint.x.toFloat(),
            centerPoint.y.toFloat(),
            radiusPx.toFloat(),
            outlinePaint
        )
    }
}

internal fun circleRadiusPx(
    mapView: MapView,
    centerLat: Double,
    centerLon: Double,
    radiusM: Double,
    centerX: Int,
    centerY: Int
): Double {
    val projection = mapView.projection
    val northPoint = destinationPoint(centerLat, centerLon, radiusM, 0.0)
    val northPixels = projection.toPixels(GeoPoint(northPoint.first, northPoint.second), null)
    val eastPoint = destinationPoint(centerLat, centerLon, radiusM, 90.0)
    val eastPixels = projection.toPixels(GeoPoint(eastPoint.first, eastPoint.second), null)
    return (
        hypot((northPixels.x - centerX).toDouble(), (northPixels.y - centerY).toDouble()) +
            hypot((eastPixels.x - centerX).toDouble(), (eastPixels.y - centerY).toDouble())
        ) / 2.0
}

internal fun destinationPoint(lat: Double, lon: Double, distanceM: Double, bearingDeg: Double): Pair<Double, Double> {
    val bearing = Math.toRadians(bearingDeg)
    val angularDistance = distanceM / 6_371_000.0
    val latRad = Math.toRadians(lat)
    val lonRad = Math.toRadians(lon)
    val destLat =
        kotlin.math.asin(
            kotlin.math.sin(latRad) * kotlin.math.cos(angularDistance) +
                kotlin.math.cos(latRad) * kotlin.math.sin(angularDistance) * kotlin.math.cos(bearing)
        )
    val destLon =
        lonRad +
            kotlin.math.atan2(
                kotlin.math.sin(bearing) * kotlin.math.sin(angularDistance) * kotlin.math.cos(latRad),
                kotlin.math.cos(angularDistance) - kotlin.math.sin(latRad) * kotlin.math.sin(destLat)
            )
    return Math.toDegrees(destLat) to Math.toDegrees(destLon)
}

internal class TapOverlay(private val onTap: (Double, Double) -> Boolean) : Overlay() {
    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val projection = mapView.projection
        val geoPoint = projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
        return onTap(geoPoint.latitude, geoPoint.longitude)
    }
}
