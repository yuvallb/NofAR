package com.nofar.feature.explore

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.nofar.core.designsystem.component.NofARExploreBottomHud
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
    onScreenSizeChanged: (Float, Float) -> Unit,
    onFieldOfViewChanged: (CameraFieldOfView) -> Unit,
    onHiddenCountClick: (Int) -> Unit,
    onDismissExpandedBucket: () -> Unit,
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
            onHiddenCountClick = onHiddenCountClick
        )

        if (uiState.exploreGate == ExploreGate.READY) {
            ExploreReadyChrome(uiState = uiState, onNavigateBack = onNavigateBack)
        } else if (uiState.exploreGate != ExploreGate.GRACE_EXPIRED) {
            ExploreExitButton(onNavigateBack = onNavigateBack)
        }

        if (uiState.exploreGate != ExploreGate.GRACE_EXPIRED) {
            ExploreOsmWatermark(modifier = Modifier.align(Alignment.BottomEnd))
        }

        debugOverlay()

        if (uiState.exploreGate == ExploreGate.GRACE_EXPIRED) {
            ExploreGraceExpiredDialog(
                regionName = uiState.activeRegionName.orEmpty(),
                onExit = onNavigateBack
            )
        }

        uiState.expandedCluster?.let { cluster ->
            ExploreExpandedBucketDialog(cluster = cluster, onDismiss = onDismissExpandedBucket)
        }
    }
}

@Composable
private fun BoxScope.ExploreGateContent(
    gate: ExploreGate,
    uiState: ExploreUiState,
    permissionState: PermissionState,
    onNavigateBack: () -> Unit,
    onFieldOfViewChanged: (CameraFieldOfView) -> Unit,
    onHiddenCountClick: (Int) -> Unit
) {
    when (gate) {
        ExploreGate.READY -> {
            if (permissionState.cameraGranted) {
                ExploreCameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onFieldOfViewChanged = onFieldOfViewChanged
                )
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                ExploreArOverlay(uiState = uiState, onHiddenCountClick = onHiddenCountClick)
            }
        }
        ExploreGate.WAITING_GPS -> {
            ExploreWaitingForGpsOverlay()
            LocationPermissionBanner(
                permissionState = permissionState,
                waitingForGpsFix = uiState.waitingForGpsFix,
                modifier = Modifier.align(Alignment.Center)
            )
        }
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
private fun BoxScope.ExploreReadyChrome(uiState: ExploreUiState, onNavigateBack: () -> Unit) {
    ExploreCompassRibbon(uiState = uiState)
    CompassCalibrationHint(
        calibrationState = uiState.calibrationState,
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp)
    )
    if (uiState.partialRegionWarning) {
        ExplorePartialRegionBanner(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 120.dp)
        )
    }
    if (uiState.showRegionExitBanner) {
        ExploreRegionExitBanner(
            regionName = uiState.activeRegionName.orEmpty(),
            graceSecondsRemaining = uiState.regionExitGraceSecondsRemaining,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 120.dp)
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
        modifier = Modifier.align(Alignment.BottomCenter)
    )
    ExploreExitButton(onNavigateBack = onNavigateBack)
}
