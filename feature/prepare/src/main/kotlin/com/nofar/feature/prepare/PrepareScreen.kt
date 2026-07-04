@file:Suppress("LongMethod", "MaxLineLength")

package com.nofar.feature.prepare

import android.view.MotionEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nofar.core.data.prepare.PreparePhase
import com.nofar.core.designsystem.component.NofARTopAppBar
import com.nofar.core.model.AppConfig
import com.nofar.core.model.DownloadStatus
import kotlin.math.roundToInt
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon

@Composable
fun PrepareScreen(modifier: Modifier = Modifier, viewModel: PrepareViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    DisposableEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        onDispose { }
    }

    Column(modifier = modifier.fillMaxSize()) {
        NofARTopAppBar(title = "Prepare")
        Box(modifier = Modifier.weight(1f)) {
            PrepareMap(
                centerLat = uiState.centerLat,
                centerLon = uiState.centerLon,
                radiusKm = uiState.radiusKm,
                onMapTap = viewModel::onMapTap,
                modifier = Modifier.fillMaxSize()
            )
            PrepareControlPanel(
                uiState = uiState,
                onRegionNameChanged = viewModel::onRegionNameChanged,
                onRadiusChanged = viewModel::onRadiusChanged,
                onDownloadClicked = viewModel::onDownloadClicked,
                onCancelDownload = viewModel::cancelDownload,
                onRetry = viewModel::retryDownload,
                modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
    }

    if (uiState.showCellularWarning) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCellularWarning,
            title = { Text("Large cellular download") },
            text = {
                Text(
                    "Estimated download size is ${formatMegabytes(uiState.estimateBytes)} MB. " +
                        "Continue on mobile data?"
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmCellularDownload) {
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCellularWarning) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PrepareMap(
    centerLat: Double,
    centerLon: Double,
    radiusKm: Double,
    onMapTap: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val center = remember(centerLat, centerLon) { GeoPoint(centerLat, centerLon) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                setMultiTouchControls(true)
                controller.setZoom(10.0)
                controller.setCenter(center)
                overlays.add(
                    TapOverlay { lat, lon ->
                        onMapTap(lat, lon)
                        true
                    }
                )
            }
        },
        update = { mapView ->
            mapView.controller.setCenter(GeoPoint(centerLat, centerLon))
            mapView.overlays.removeAll { it is Marker || it is Polygon }
            mapView.overlays.add(
                Marker(mapView).apply {
                    position = GeoPoint(centerLat, centerLon)
                    isDraggable = false
                }
            )
            mapView.overlays.add(
                Polygon(mapView).apply {
                    points = circlePoints(centerLat, centerLon, radiusKm * 1000)
                    fillPaint.color = 0x3300A884
                    outlinePaint.color = 0xFF00A884.toInt()
                    outlinePaint.strokeWidth = 4f
                }
            )
            mapView.invalidate()
        }
    )
}

@Composable
private fun PrepareControlPanel(
    uiState: PrepareUiState,
    onRegionNameChanged: (String) -> Unit,
    onRadiusChanged: (Double) -> Unit,
    onDownloadClicked: () -> Unit,
    onCancelDownload: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = uiState.regionName,
            onValueChange = onRegionNameChanged,
            label = { Text("Region name") },
            isError = uiState.nameError != null,
            supportingText = uiState.nameError?.let { { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.downloadUiState != PrepareDownloadUiState.DOWNLOADING
        )

        Text(
            text = "Radius: ${"%.1f".format(uiState.radiusKm)} km",
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = uiState.radiusKm.toFloat(),
            onValueChange = { onRadiusChanged(it.toDouble()) },
            valueRange = AppConfig.REGION_RADIUS_MIN_KM.toFloat()..AppConfig.REGION_RADIUS_MAX_KM.toFloat(),
            enabled = uiState.downloadUiState != PrepareDownloadUiState.DOWNLOADING
        )
        val atRadiusBound =
            uiState.radiusKm <= AppConfig.REGION_RADIUS_MIN_KM ||
                uiState.radiusKm >= AppConfig.REGION_RADIUS_MAX_KM
        if (atRadiusBound) {
            val minKm = AppConfig.REGION_RADIUS_MIN_KM.toInt()
            val maxKm = AppConfig.REGION_RADIUS_MAX_KM.toInt()
            Text(
                text = "Radius is clamped to $minKm–$maxKm km",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        EstimatePanel(
            estimateBytes = uiState.estimateBytes,
            demTileCount = uiState.demTileCount
        )

        when (uiState.downloadUiState) {
            PrepareDownloadUiState.DOWNLOADING, PrepareDownloadUiState.ESTIMATING -> {
                ProgressPanel(progress = uiState.progress)
                OutlinedButton(onClick = onCancelDownload, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
            PrepareDownloadUiState.ERROR -> {
                uiState.errorMessage?.let { message ->
                    Text(text = message, color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text("Retry")
                }
            }
            PrepareDownloadUiState.COMPLETE -> {
                val status = uiState.existingRegion?.downloadStatus ?: DownloadStatus.READY
                Text(
                    text =
                    when (status) {
                        DownloadStatus.PARTIAL -> "Download complete with partial DEM coverage."
                        DownloadStatus.READY -> "Region is ready for Explore."
                        else -> "Download complete."
                    }
                )
            }
            else -> {
                Button(onClick = onDownloadClicked, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (uiState.existingRegion?.downloadStatus == DownloadStatus.PARTIAL) {
                            "Re-download"
                        } else {
                            "Download"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EstimatePanel(estimateBytes: Long, demTileCount: Int) {
    Column {
        Text(
            text = "Estimated size: ~${formatMegabytes(estimateBytes)} MB",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "DEM tiles: $demTileCount × 13–50 MB",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ProgressPanel(progress: com.nofar.core.data.prepare.PrepareProgress?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (progress == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                Text("Starting download…")
            }
        } else {
            val phaseLabel =
                when (progress.phase) {
                    PreparePhase.OSM -> "OSM download"
                    PreparePhase.DEM -> "DEM tile ${progress.demTileIndex}/${progress.demTileCount}"
                    PreparePhase.POST_PROCESSING -> "Post-processing"
                }
            Text(text = phaseLabel)
            LinearProgressIndicator(
                progress = { progress.overallPercent / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            Text("${progress.overallPercent}% · ~${formatMegabytes(progress.remainingBytesEstimate)} MB remaining")
        }
    }
}

private fun formatMegabytes(bytes: Long): String = (bytes / (1024.0 * 1024.0)).roundToInt().toString()

private fun circlePoints(centerLat: Double, centerLon: Double, radiusM: Double): List<GeoPoint> {
    val points = mutableListOf<GeoPoint>()
    repeat(64) { index ->
        val bearing = index * 360.0 / 64.0
        val destination = destinationPoint(centerLat, centerLon, radiusM, bearing)
        points += GeoPoint(destination.first, destination.second)
    }
    return points
}

private fun destinationPoint(lat: Double, lon: Double, distanceM: Double, bearingDeg: Double): Pair<Double, Double> {
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

private class TapOverlay(private val onTap: (Double, Double) -> Boolean) : Overlay() {
    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val projection = mapView.projection
        val geoPoint = projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
        return onTap(geoPoint.latitude, geoPoint.longitude)
    }
}
