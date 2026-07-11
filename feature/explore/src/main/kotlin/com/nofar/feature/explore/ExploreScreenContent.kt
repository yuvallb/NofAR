package com.nofar.feature.explore

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.nofar.core.designsystem.component.NofARExploreBottomHud
import com.nofar.core.designsystem.util.NofARFormatters
import com.nofar.core.model.AppConfig
import com.nofar.core.ui.compass.CompassCalibrationHint
import com.nofar.core.ui.location.LocationPermissionBanner
import com.nofar.core.ui.permission.PermissionState
import com.nofar.core.visibility.CameraFieldOfView

@Composable
internal fun ExploreScreenRoot(
    uiState: ExploreUiState,
    permissionState: PermissionState,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onScreenSizeChanged: (Float, Float) -> Unit,
    onFieldOfViewChanged: (CameraFieldOfView) -> Unit,
    onHiddenCountClick: (Int) -> Unit,
    onDismissExpandedBucket: () -> Unit,
    onDownloadRegionConfirmed: () -> Unit,
    onDownloadPromptDismissed: () -> Unit,
    onShowDownloadPrompt: () -> Unit,
    onConfirmCellularDownload: () -> Unit,
    onDismissCellularWarning: () -> Unit,
    onDismissWifiOnlyBlocked: () -> Unit,
    modifier: Modifier = Modifier,
    debugOverlay: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier =
        modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                onScreenSizeChanged(size.width.toFloat(), size.height.toFloat())
            }
    ) {
        ExploreGateContent(
            gate = uiState.exploreGate,
            uiState = uiState,
            permissionState = permissionState,
            onNavigateBack = onNavigateBack,
            onFieldOfViewChanged = onFieldOfViewChanged,
            onHiddenCountClick = onHiddenCountClick,
            onDownloadRegionConfirmed = onDownloadRegionConfirmed,
            onDownloadPromptDismissed = onDownloadPromptDismissed,
            onShowDownloadPrompt = onShowDownloadPrompt
        )

        if (uiState.exploreGate == ExploreGate.READY) {
            ExploreReadyChrome(
                uiState = uiState,
                onNavigateBack = onNavigateBack,
                onNavigateToSettings = onNavigateToSettings
            )
        } else {
            ExploreTopChrome(
                uiState = uiState,
                onNavigateBack = onNavigateBack,
                onNavigateToSettings = onNavigateToSettings
            )
        }

        if (uiState.exploreGate == ExploreGate.GRACE_EXPIRED) {
            ExploreGraceExpiredDialog(
                regionName = uiState.activeRegionName.orEmpty(),
                onExit = onNavigateBack
            )
        }

        uiState.expandedCluster?.let { cluster ->
            ExploreExpandedBucketDialog(cluster = cluster, onDismiss = onDismissExpandedBucket)
        }

        if (uiState.showCellularWarning && uiState.downloadPrompt != null) {
            ExploreCellularWarningDialog(
                demTileCount = uiState.downloadPrompt.demTileCount,
                estimateDisplay = NofARFormatters.formatMegabytes(uiState.downloadPrompt.estimateBytes),
                onDownloadAnyway = onConfirmCellularDownload,
                onDismiss = onDismissCellularWarning
            )
        }

        if (uiState.showWifiOnlyBlocked) {
            ExploreWifiOnlyBlockedDialog(onDismiss = onDismissWifiOnlyBlocked)
        }

        if (uiState.exploreGate != ExploreGate.GRACE_EXPIRED) {
            ExploreOsmWatermark(modifier = Modifier.align(Alignment.BottomEnd))
        }

        debugOverlay()
    }
}

@Composable
private fun BoxScope.ExploreTopChrome(
    uiState: ExploreUiState,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    if (uiState.simpleModeEnabled) {
        ExploreSettingsButton(onNavigateToSettings = onNavigateToSettings)
    } else if (uiState.exploreGate != ExploreGate.GRACE_EXPIRED) {
        ExploreExitButton(onNavigateBack = onNavigateBack)
    }
}

@Composable
private fun BoxScope.ExploreGateContent(
    gate: ExploreGate,
    uiState: ExploreUiState,
    permissionState: PermissionState,
    onNavigateBack: () -> Unit,
    onFieldOfViewChanged: (CameraFieldOfView) -> Unit,
    onHiddenCountClick: (Int) -> Unit,
    onDownloadRegionConfirmed: () -> Unit,
    onDownloadPromptDismissed: () -> Unit,
    onShowDownloadPrompt: () -> Unit
) {
    when (gate) {
        ExploreGate.READY -> ExploreReadyGateContent(
            uiState = uiState,
            permissionState = permissionState,
            onFieldOfViewChanged = onFieldOfViewChanged,
            onHiddenCountClick = onHiddenCountClick
        )
        ExploreGate.REGION_DOWNLOAD_DISMISSED -> ExploreSimpleModeCameraGate(
            permissionState = permissionState,
            onFieldOfViewChanged = onFieldOfViewChanged
        ) {
            ExploreDownloadDismissedBanner(
                onShowDownloadPrompt = onShowDownloadPrompt,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)
            )
        }
        ExploreGate.REGION_DOWNLOAD_NEEDED -> ExploreSimpleModeCameraGate(
            permissionState = permissionState,
            onFieldOfViewChanged = onFieldOfViewChanged
        ) {
            uiState.downloadPrompt?.let { proposal ->
                ExploreDownloadPromptOverlay(
                    proposalName = proposal.name,
                    estimateDisplay = NofARFormatters.formatMegabytes(proposal.estimateBytes),
                    demTileCount = proposal.demTileCount,
                    errorMessage = uiState.downloadUiMessage,
                    onDownload = onDownloadRegionConfirmed,
                    onDismiss = onDownloadPromptDismissed
                )
            }
        }
        ExploreGate.REGION_DOWNLOADING -> ExploreSimpleModeCameraGate(
            permissionState = permissionState,
            onFieldOfViewChanged = onFieldOfViewChanged
        ) {
            ExploreDownloadProgressOverlay(progressPct = uiState.downloadProgressPct)
        }
        ExploreGate.WAITING_GPS -> ExploreWaitingGpsGateContent(
            uiState = uiState,
            permissionState = permissionState
        )
        ExploreGate.LOCATION_DENIED -> {
            LocationPermissionBanner(
                permissionState = permissionState,
                waitingForGpsFix = false,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        ExploreGate.CAMERA_DENIED -> ExploreCameraPermissionOverlay(permissionState = permissionState)
        ExploreGate.COMPASS_UNAVAILABLE -> ExploreCompassUnavailableOverlay(onExit = onNavigateBack)
        ExploreGate.REGION_MISSING -> ExploreRegionDataErrorOverlay(onExit = onNavigateBack)
        ExploreGate.REGION_OUTSIDE -> Unit
        ExploreGate.GRACE_EXPIRED -> Unit
    }
}

@Composable
private fun BoxScope.ExploreReadyGateContent(
    uiState: ExploreUiState,
    permissionState: PermissionState,
    onFieldOfViewChanged: (CameraFieldOfView) -> Unit,
    onHiddenCountClick: (Int) -> Unit
) {
    if (permissionState.cameraGranted) {
        ExploreCameraPreview(
            modifier = Modifier.fillMaxSize(),
            onFieldOfViewChanged = onFieldOfViewChanged
        )
    }
    ExploreArOverlay(uiState = uiState, onHiddenCountClick = onHiddenCountClick)
}

@Composable
private fun BoxScope.ExploreSimpleModeCameraGate(
    permissionState: PermissionState,
    onFieldOfViewChanged: (CameraFieldOfView) -> Unit,
    overlay: @Composable BoxScope.() -> Unit
) {
    if (permissionState.cameraGranted) {
        ExploreCameraPreview(
            modifier = Modifier.fillMaxSize(),
            onFieldOfViewChanged = onFieldOfViewChanged
        )
    }
    overlay()
}

@Composable
private fun BoxScope.ExploreWaitingGpsGateContent(uiState: ExploreUiState, permissionState: PermissionState) {
    ExploreWaitingForGpsOverlay()
    LocationPermissionBanner(
        permissionState = permissionState,
        waitingForGpsFix = uiState.waitingForGpsFix,
        modifier = Modifier.align(Alignment.Center)
    )
}

@Composable
private fun BoxScope.ExploreReadyChrome(
    uiState: ExploreUiState,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    ExploreCompassRibbon(uiState = uiState)
    CompassCalibrationHint(
        calibrationState = uiState.calibrationState,
        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 72.dp)
    )
    if (uiState.partialRegionWarning) {
        ExplorePartialRegionBanner(
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 120.dp)
        )
    }
    if (uiState.showRegionExitBanner) {
        ExploreRegionExitBanner(
            regionName = uiState.activeRegionName.orEmpty(),
            graceSecondsRemaining = uiState.regionExitGraceSecondsRemaining,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 120.dp)
        )
    }
    if (uiState.showNoVisibleEntitiesHint) {
        ExploreNoVisibleEntitiesHint(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)
        )
    }
    NofARExploreBottomHud(
        altitudeM = uiState.altitudeM ?: "—",
        maxRangeKm = AppConfig.REGION_RADIUS_MAX_KM.toInt(),
        simpleMode = uiState.simpleModeEnabled,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
    if (uiState.simpleModeEnabled) {
        ExploreSettingsButton(onNavigateToSettings = onNavigateToSettings)
    } else {
        ExploreExitButton(onNavigateBack = onNavigateBack)
    }
}
