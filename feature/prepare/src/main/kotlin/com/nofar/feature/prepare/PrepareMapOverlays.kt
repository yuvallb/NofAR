@file:Suppress("MaxLineLength")

package com.nofar.feature.prepare

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.nofar.core.model.CoverageSet
import com.nofar.core.model.DemTileId
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
    selectedCellIds: Set<String>,
    downloadedCoverageSets: List<CoverageSet>,
    downloadedCellIdsBySet: Map<UUID, List<String>>,
    excludeRegionId: UUID?,
    mapRecenterNonce: Long,
    onMapTap: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    PrepareMapCore(
        centerLat = centerLat,
        centerLon = centerLon,
        selectedCellIds = selectedCellIds,
        downloadedCoverageSets = downloadedCoverageSets,
        downloadedCellIdsBySet = downloadedCellIdsBySet,
        excludeRegionId = excludeRegionId,
        mapRecenterNonce = mapRecenterNonce,
        onMapTap = onMapTap,
        modifier = modifier,
        showSelectionCells = true
    )
}

@Composable
internal fun PreparePointPickerMap(
    selectedLat: Double,
    selectedLon: Double,
    downloadedCoverageSets: List<CoverageSet>,
    downloadedCellIdsBySet: Map<UUID, List<String>>,
    mapRecenterNonce: Long,
    onMapTap: (Double, Double) -> Unit,
    visibilityMask: MapVisibilityPreviewMask? = null,
    modifier: Modifier = Modifier
) {
    PrepareMapCore(
        centerLat = selectedLat,
        centerLon = selectedLon,
        selectedCellIds = emptySet(),
        downloadedCoverageSets = downloadedCoverageSets,
        downloadedCellIdsBySet = downloadedCellIdsBySet,
        excludeRegionId = null,
        mapRecenterNonce = mapRecenterNonce,
        onMapTap = onMapTap,
        modifier = modifier,
        showSelectionCells = false,
        visibilityMask = visibilityMask
    )
}

@Composable
private fun PrepareMapCore(
    centerLat: Double,
    centerLon: Double,
    selectedCellIds: Set<String>,
    downloadedCoverageSets: List<CoverageSet>,
    downloadedCellIdsBySet: Map<UUID, List<String>>,
    excludeRegionId: UUID?,
    mapRecenterNonce: Long,
    onMapTap: (Double, Double) -> Unit,
    showSelectionCells: Boolean,
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
                selectedCellIds = selectedCellIds,
                showSelectionCells = showSelectionCells,
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
                selectedCellIds = selectedCellIds,
                downloadedCoverageSets = downloadedCoverageSets,
                downloadedCellIdsBySet = downloadedCellIdsBySet,
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
    selectedCellIds: Set<String>,
    showSelectionCells: Boolean,
    onMapTap: (Double, Double) -> Unit,
    mapHolder: PrepareMapHolder
): MapView = MapView(context).apply {
    setMultiTouchControls(true)
    controller.setZoom(10.0)
    controller.setCenter(GeoPoint(centerLat, centerLon))
    val downloadedOverlay = DownloadedCoverageCellsOverlay()
    val selectionOverlay =
        if (showSelectionCells) {
            DownloadCellRingOverlay().apply { cellIds = selectedCellIds.sorted() }
        } else {
            null
        }
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
    selectionOverlay?.let { overlays.add(it) }
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
    mapHolder.selectionOverlay = selectionOverlay
    mapHolder.marker = marker
    mapHolder.visibilityOverlay = visibilityOverlay
    mapHolder.mapView = this
}

private fun updatePrepareMapView(
    mapView: MapView,
    mapHolder: PrepareMapHolder,
    centerLat: Double,
    centerLon: Double,
    selectedCellIds: Set<String>,
    downloadedCoverageSets: List<CoverageSet>,
    downloadedCellIdsBySet: Map<UUID, List<String>>,
    excludeRegionId: UUID?,
    visibilityMask: MapVisibilityPreviewMask?,
    mapRecenterNonce: Long,
    lastRecenterNonce: Long,
    onRecenterApplied: (Long) -> Unit
) {
    mapHolder.marker?.position = GeoPoint(centerLat, centerLon)
    mapHolder.selectionOverlay?.apply {
        cellIds = selectedCellIds.sorted()
    }
    mapHolder.downloadedOverlay?.apply {
        coverageSets =
            downloadedCoverageSets.filter { excludeRegionId == null || it.id != excludeRegionId }
        cellIdsBySet = downloadedCellIdsBySet
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
    var selectionOverlay: DownloadCellRingOverlay? = null
    var downloadedOverlay: DownloadedCoverageCellsOverlay? = null
    var visibilityOverlay: ObserverVisibilityPreviewOverlay? = null
}

internal class DownloadedCoverageCellsOverlay : Overlay() {
    var coverageSets: List<CoverageSet> = emptyList()
    var cellIdsBySet: Map<UUID, List<String>> = emptyMap()
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
        coverageSets.forEach { set ->
            val cellIds = cellIdsBySet[set.id].orEmpty()
            cellIds.forEach { cellId -> drawCell(canvas, mapView, cellId, fillPaint, outlinePaint) }
            val labelCenter = cellCenter(cellIds.firstOrNull()) ?: return@forEach
            drawCoverageSetLabel(canvas, mapView, labelCenter.first, labelCenter.second, set.name)
        }
    }

    private fun drawCoverageSetLabel(
        canvas: Canvas,
        mapView: MapView,
        centerLat: Double,
        centerLon: Double,
        name: String
    ) {
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

internal class DownloadCellRingOverlay : Overlay() {
    var cellIds: List<String> = emptyList()

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
        cellIds.forEach { cellId -> drawCell(canvas, mapView, cellId, fillPaint, outlinePaint) }
    }
}

private fun drawCell(
    canvas: Canvas,
    mapView: MapView,
    cellId: String,
    fillPaint: Paint? = null,
    outlinePaint: Paint? = null
) {
    val parsed = DemTileId.parse(cellId) ?: return
    val (tileLat, tileLon) = parsed
    val minLat = tileLat.toDouble()
    val maxLat = minLat + 1.0
    val minLon = tileLon.toDouble()
    val maxLon = minLon + 1.0
    val projection = mapView.projection
    val nw = projection.toPixels(GeoPoint(maxLat, minLon), null)
    val se = projection.toPixels(GeoPoint(minLat, maxLon), null)
    val path =
        Path().apply {
            moveTo(nw.x.toFloat(), nw.y.toFloat())
            lineTo(se.x.toFloat(), nw.y.toFloat())
            lineTo(se.x.toFloat(), se.y.toFloat())
            lineTo(nw.x.toFloat(), se.y.toFloat())
            close()
        }
    fillPaint?.let { canvas.drawPath(path, it) }
    outlinePaint?.let { canvas.drawPath(path, it) }
}

private fun cellCenter(cellId: String?): Pair<Double, Double>? {
    val parsed = cellId?.let { DemTileId.parse(it) } ?: return null
    val (tileLat, tileLon) = parsed
    return (tileLat + 0.5) to (tileLon + 0.5)
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
