@file:Suppress("LongMethod", "MaxLineLength")

package com.nofar.feature.prepare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.nofar.core.model.LabelLanguage
import com.nofar.core.ui.LabelLanguageDropdown
import com.nofar.core.ui.PrepareOverlayDarkText
import com.nofar.core.ui.PrepareOverlayDarkTextSecondary
import com.nofar.core.ui.permission.rememberNofARPermissionState
import org.osmdroid.config.Configuration

@Composable
fun PrepareScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
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
    var showHelpDialog by remember { mutableStateOf(false) }

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
                    TextButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Settings",
                            tint = NofARColors.TextSecondary
                        )
                    }
                } else {
                    TextButton(onClick = { showHelpDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Help,
                            contentDescription = "Help",
                            tint = NofARColors.TextSecondary
                        )
                    }
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
                onLabelLanguageChanged = viewModel::onLabelLanguageChanged,
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
    if (showHelpDialog) {
        PrepareHelpDialog(onDismiss = { showHelpDialog = false })
    }
}

@Composable
private fun PrepareHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Prepare help") },
        text = {
            Text(
                "Tap the map to place a circular region, adjust the radius, then download " +
                    "OpenStreetMap places and elevation tiles for offline Explore. " +
                    "Downloads require a network connection and only run in Prepare mode."
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
private fun DefineRegionContent(
    uiState: PrepareUiState,
    onMapTap: (Double, Double) -> Unit,
    onMoveToCurrentLocation: () -> Unit,
    onRegionNameChanged: (String) -> Unit,
    onLabelLanguageChanged: (LabelLanguage) -> Unit,
    onRadiusChanged: (Double) -> Unit,
    onDownloadClicked: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        PrepareRegionMap(
            centerLat = uiState.centerLat,
            centerLon = uiState.centerLon,
            radiusKm = uiState.radiusKm,
            downloadedRegions = uiState.downloadedRegions,
            excludeRegionId = uiState.regionId ?: uiState.existingRegion?.id,
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
                .windowInsetsPadding(bottomControlsInsets())
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
                colors = outlinedFieldColors(darkForeground = true)
            )

            Text(
                text = stringResource(com.nofar.core.ui.R.string.prepare_label_language),
                style = MaterialTheme.typography.bodyMedium,
                color = PrepareOverlayDarkText
            )
            LabelLanguageDropdown(
                selected = uiState.labelLanguage,
                enabled = !uiState.labelLanguageLocked,
                onSelected = onLabelLanguageChanged,
                darkForeground = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (uiState.labelLanguageLocked) {
                Text(
                    text = stringResource(com.nofar.core.ui.R.string.prepare_label_language_locked_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = PrepareOverlayDarkTextSecondary
                )
            }

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
                    NofARPrimaryButton(
                        text = "RE-DOWNLOAD DATA",
                        onClick = onDownloadClicked,
                        modifier = Modifier.fillMaxWidth()
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
                .windowInsetsPadding(bottomControlsInsets())
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
private fun outlinedFieldColors(darkForeground: Boolean = false) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NofARColors.PrimaryYellow,
    unfocusedBorderColor = if (darkForeground) PrepareOverlayDarkTextSecondary else NofARColors.TextCaption,
    focusedLabelColor = NofARColors.PrimaryYellow,
    unfocusedLabelColor = if (darkForeground) PrepareOverlayDarkTextSecondary else NofARColors.TextSecondary,
    cursorColor = NofARColors.PrimaryYellow,
    focusedTextColor = if (darkForeground) PrepareOverlayDarkText else NofARColors.TextPrimary,
    unfocusedTextColor = if (darkForeground) PrepareOverlayDarkText else NofARColors.TextPrimary,
    errorSupportingTextColor = NofARColors.ErrorDestructive,
    errorLabelColor = NofARColors.ErrorDestructive
)
