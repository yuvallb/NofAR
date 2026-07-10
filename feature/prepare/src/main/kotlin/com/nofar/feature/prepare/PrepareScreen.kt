@file:Suppress("LongMethod", "MaxLineLength")

package com.nofar.feature.prepare

import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nofar.core.designsystem.component.NofARBackTopBar
import com.nofar.core.designsystem.component.NofARDownloadPipeline
import com.nofar.core.designsystem.component.NofAREstimatePanel
import com.nofar.core.designsystem.component.NofARPrimaryButton
import com.nofar.core.designsystem.component.NofARSecondaryOutlinedButton
import com.nofar.core.designsystem.theme.NofARColors
import com.nofar.core.designsystem.util.NofARFormatters
import com.nofar.core.model.AppConfig
import com.nofar.core.model.DownloadStatus
import com.nofar.core.ui.permission.rememberNofARPermissionState
import kotlin.math.hypot
import kotlin.math.roundToInt
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay

@Composable
fun PrepareScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PrepareViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionState = rememberNofARPermissionState()
    val isDownloading =
        uiState.downloadUiState == PrepareDownloadUiState.DOWNLOADING ||
            uiState.downloadUiState == PrepareDownloadUiState.ESTIMATING
    var trackDownloadForAutoNavigate by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.downloadUiState) {
        if (uiState.downloadUiState == PrepareDownloadUiState.DOWNLOADING ||
            uiState.downloadUiState == PrepareDownloadUiState.ESTIMATING
        ) {
            trackDownloadForAutoNavigate = true
        } else if (trackDownloadForAutoNavigate &&
            uiState.downloadUiState == PrepareDownloadUiState.COMPLETE
        ) {
            onNavigateBack()
        }
    }

    DisposableEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        onDispose { }
    }

    LaunchedEffect(permissionState.locationAccessState) {
        viewModel.onLocationPermissionChanged(permissionState.locationAccessState)
    }

    Column(modifier = modifier.fillMaxSize()) {
        val title =
            if (isDownloading && uiState.regionName.isNotBlank()) {
                "Downloading: ${uiState.regionName}"
            } else {
                "Prepare Region"
            }
        NofARBackTopBar(
            title = title,
            onNavigateBack = onNavigateBack,
            navigationIcon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = NofARColors.PrimaryYellow
                )
            },
            actions = {
                if (isDownloading) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = NofARColors.TextSecondary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Help,
                        contentDescription = "Help",
                        tint = NofARColors.TextSecondary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        )

        if (isDownloading) {
            DownloadingContent(
                uiState = uiState,
                onCancelDownload = viewModel::cancelDownload,
                modifier = Modifier.weight(1f)
            )
        } else {
            DefineRegionContent(
                uiState = uiState,
                onMapTap = viewModel::onMapTap,
                onMoveToCurrentLocation = {
                    if (permissionState.fineLocationGranted) {
                        viewModel.moveToCurrentLocation()
                    } else {
                        permissionState.requestFineLocation()
                    }
                },
                onRegionNameChanged = viewModel::onRegionNameChanged,
                onRadiusChanged = viewModel::onRadiusChanged,
                onDownloadClicked = viewModel::onDownloadClicked,
                onRetry = viewModel::retryDownload,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (uiState.showCellularWarning) {
        CellularWarningDialog(
            demTileCount = uiState.demTileCount,
            estimateBytes = uiState.estimateBytes,
            onDownloadAnyway = viewModel::confirmCellularDownload,
            onDismiss = viewModel::dismissCellularWarning
        )
    }
    if (uiState.showWifiOnlyBlocked) {
        WifiOnlyBlockedDialog(onDismiss = viewModel::dismissWifiOnlyBlocked)
    }
}

@Composable
private fun DefineRegionContent(
    uiState: PrepareUiState,
    onMapTap: (Double, Double) -> Unit,
    onMoveToCurrentLocation: () -> Unit,
    onRegionNameChanged: (String) -> Unit,
    onRadiusChanged: (Double) -> Unit,
    onDownloadClicked: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        PrepareMap(
            centerLat = uiState.centerLat,
            centerLon = uiState.centerLon,
            radiusKm = uiState.radiusKm,
            mapRecenterNonce = uiState.mapRecenterNonce,
            onMapTap = onMapTap,
            modifier = Modifier.fillMaxSize()
        )
        FloatingActionButton(
            onClick = onMoveToCurrentLocation,
            modifier =
            Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            containerColor = NofARColors.SurfaceVariant,
            contentColor = NofARColors.PrimaryYellow,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "Move marker to current location",
                modifier = Modifier.size(24.dp)
            )
        }
        Column(
            modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
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
                colors = outlinedFieldColors()
            )

            Text(
                text = "Radius: ${uiState.radiusKm.roundToInt()} km",
                style = MaterialTheme.typography.bodyMedium,
                color = NofARColors.TextPrimary
            )
            Slider(
                value = uiState.radiusKm.toFloat(),
                onValueChange = { onRadiusChanged(it.toDouble()) },
                valueRange = AppConfig.REGION_RADIUS_MIN_KM.toFloat()..AppConfig.REGION_RADIUS_MAX_KM.toFloat(),
                colors =
                SliderDefaults.colors(
                    thumbColor = NofARColors.PrimaryYellow,
                    activeTrackColor = NofARColors.PrimaryYellow,
                    inactiveTrackColor = NofARColors.SurfaceVariant
                )
            )

            val demBytes = (uiState.estimateBytes * 0.57).toLong().coerceAtLeast(0L)
            val osmBytes = (uiState.estimateBytes - demBytes).coerceAtLeast(0L)
            NofAREstimatePanel(
                osmEstimateBytes = osmBytes,
                demEstimateBytes = demBytes,
                totalEstimateBytes = uiState.estimateBytes,
                demTileCount = uiState.demTileCount
            )

            when (uiState.downloadUiState) {
                PrepareDownloadUiState.ERROR -> {
                    uiState.errorMessage?.let { message ->
                        Text(text = message, color = NofARColors.ErrorDestructive)
                    }
                    NofARPrimaryButton(text = "RETRY", onClick = onRetry, modifier = Modifier.fillMaxWidth())
                }
                PrepareDownloadUiState.COMPLETE -> {
                    val status = uiState.existingRegion?.downloadStatus ?: DownloadStatus.READY
                    Text(
                        text =
                        when (status) {
                            DownloadStatus.PARTIAL -> "Download complete with partial DEM coverage."
                            DownloadStatus.READY -> "Region is ready for Explore."
                            else -> "Download complete."
                        },
                        color = NofARColors.TextSecondary
                    )
                }
                else -> {
                    val label =
                        if (uiState.existingRegion?.downloadStatus == DownloadStatus.PARTIAL) {
                            "RE-DOWNLOAD DATA"
                        } else {
                            "DOWNLOAD DATA"
                        }
                    NofARPrimaryButton(text = label, onClick = onDownloadClicked, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun DownloadingContent(uiState: PrepareUiState, onCancelDownload: () -> Unit, modifier: Modifier = Modifier) {
    val progress = uiState.progress
    val steps = buildPipelineSteps(uiState)
    val overallPercent = progress?.overallPercent ?: 0

    Column(modifier = modifier.fillMaxSize()) {
        NofARDownloadPipeline(
            regionName = uiState.regionName,
            steps = steps,
            overallPercent = overallPercent,
            estimatedTimeRemaining = null,
            modifier = Modifier.weight(1f)
        )
        NofARSecondaryOutlinedButton(
            text = "PAUSE DOWNLOAD",
            onClick = onCancelDownload,
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
    }
}

@Composable
private fun WifiOnlyBlockedDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wi-Fi only downloads") },
        text = {
            Text(
                "Wi-Fi only downloads are enabled in Settings. Connect to Wi-Fi before downloading map data."
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
private fun CellularWarningDialog(
    demTileCount: Int,
    estimateBytes: Long,
    onDownloadAnyway: () -> Unit,
    onDismiss: () -> Unit
) {
    var dontShowAgain by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Large Download Warning") },
        text = {
            Column {
                Text(
                    "This region requires $demTileCount DEM tiles " +
                        "(~${NofARFormatters.formatMegabytes(estimateBytes)}). " +
                        "Download over cellular or wait for Wi-Fi?"
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = dontShowAgain, onCheckedChange = { dontShowAgain = it })
                    Text("Don't show again")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDownloadAnyway) {
                Text("DOWNLOAD ANYWAY")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("WI-FI ONLY")
            }
        }
    )
}

@Composable
private fun outlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NofARColors.PrimaryYellow,
    unfocusedBorderColor = NofARColors.TextCaption,
    focusedLabelColor = NofARColors.PrimaryYellow,
    cursorColor = NofARColors.PrimaryYellow,
    focusedTextColor = NofARColors.TextPrimary,
    unfocusedTextColor = NofARColors.TextPrimary
)

@Composable
private fun PrepareMap(
    centerLat: Double,
    centerLon: Double,
    radiusKm: Double,
    mapRecenterNonce: Long,
    onMapTap: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var lastRecenterNonce by remember { mutableStateOf(0L) }
    val mapHolder = remember { PrepareMapHolder() }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                setMultiTouchControls(true)
                controller.setZoom(10.0)
                controller.setCenter(GeoPoint(centerLat, centerLon))
                val circleOverlay = RadiusCircleOverlay(centerLat, centerLon, radiusKm * 1000)
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
                overlays.add(circleOverlay)
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
                mapHolder.circleOverlay = circleOverlay
                mapHolder.marker = marker
                mapHolder.mapView = this
            }
        },
        update = { mapView ->
            mapHolder.marker?.position = GeoPoint(centerLat, centerLon)
            mapHolder.circleOverlay?.apply {
                this.centerLat = centerLat
                this.centerLon = centerLon
                this.radiusM = radiusKm * 1000
            }
            if (mapRecenterNonce != lastRecenterNonce) {
                mapView.controller.animateTo(GeoPoint(centerLat, centerLon))
                lastRecenterNonce = mapRecenterNonce
            }
            mapView.invalidate()
        }
    )
}

private class PrepareMapHolder {
    var mapView: MapView? = null
    var marker: Marker? = null
    var circleOverlay: RadiusCircleOverlay? = null
}

private class RadiusCircleOverlay(var centerLat: Double, var centerLon: Double, var radiusM: Double) : Overlay() {
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
        val northPoint = destinationPoint(centerLat, centerLon, radiusM, 0.0)
        val northPixels = projection.toPixels(GeoPoint(northPoint.first, northPoint.second), null)
        val eastPoint = destinationPoint(centerLat, centerLon, radiusM, 90.0)
        val eastPixels = projection.toPixels(GeoPoint(eastPoint.first, eastPoint.second), null)
        val radiusPx =
            (
                hypot(
                    (northPixels.x - centerPoint.x).toDouble(),
                    (northPixels.y - centerPoint.y).toDouble()
                ) +
                    hypot(
                        (eastPixels.x - centerPoint.x).toDouble(),
                        (eastPixels.y - centerPoint.y).toDouble()
                    )
                ) / 2.0
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
