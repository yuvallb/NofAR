package com.nofar.feature.explore

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nofar.core.designsystem.component.NofARArLabel
import com.nofar.core.designsystem.component.NofARCompassRibbon
import com.nofar.core.designsystem.component.NofARIconActionButton
import com.nofar.core.designsystem.theme.NofARColors
import com.nofar.core.model.LocationAccessState
import com.nofar.core.ui.permission.PermissionState
import com.nofar.core.ui.permission.rememberNofARPermissionState
import com.nofar.feature.explore.BuildConfig
import kotlin.math.roundToInt

@Composable
fun ExploreScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExploreViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionState = rememberNofARPermissionState()
    ExplorePermissionEffects(permissionState = permissionState, viewModel = viewModel)

    ExploreScreenRoot(
        modifier = modifier,
        uiState = uiState,
        permissionState = permissionState,
        onNavigateBack = onNavigateBack,
        onNavigateToSettings = onNavigateToSettings,
        onScreenSizeChanged = viewModel::onScreenSizeChanged,
        onFieldOfViewChanged = viewModel::onCameraFieldOfViewChanged,
        onHiddenCountClick = viewModel::onHiddenCountClicked,
        onDismissExpandedBucket = viewModel::onDismissExpandedBucket,
        onDownloadRegionConfirmed = viewModel::onDownloadRegionConfirmed,
        onDownloadPromptDismissed = viewModel::onDownloadPromptDismissed,
        onShowDownloadPrompt = viewModel::onShowDownloadPrompt,
        onConfirmCellularDownload = viewModel::confirmCellularDownload,
        onDismissCellularWarning = viewModel::dismissCellularWarning,
        onDismissWifiOnlyBlocked = viewModel::dismissWifiOnlyBlocked,
        debugOverlay =
        if (BuildConfig.DEBUG) {
            { ExploreDebugOverlay(uiState = uiState) }
        } else {
            {}
        }
    )
}

@Composable
private fun ExplorePermissionEffects(permissionState: PermissionState, viewModel: ExploreViewModel) {
    LaunchedEffect(permissionState.locationAccessState) {
        viewModel.onLocationPermissionChanged(permissionState.locationAccessState)
    }
    LaunchedEffect(permissionState.cameraGranted) {
        viewModel.onCameraPermissionChanged(permissionState.cameraGranted)
    }
    LaunchedEffect(permissionState.locationAccessState, permissionState.cameraGranted) {
        if (permissionState.locationAccessState == LocationAccessState.NOT_REQUESTED) {
            permissionState.requestFineLocation()
        }
        if (permissionState.fineLocationGranted && !permissionState.cameraGranted) {
            permissionState.requestCamera()
        }
    }
}

@Composable
internal fun BoxScope.ExploreArOverlay(uiState: ExploreUiState, onHiddenCountClick: (Int) -> Unit) {
    uiState.arLabels.forEach { label ->
        NofARArLabel(
            label = label,
            modifier =
            Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(label.anchorXPx.roundToInt(), label.anchorYPx.roundToInt()) },
            onHiddenCountClick = onHiddenCountClick
        )
    }
}

@Composable
internal fun BoxScope.ExploreCompassRibbon(uiState: ExploreUiState) {
    NofARCompassRibbon(
        headings = uiState.compassRibbon.headings,
        centerHeading = uiState.compassRibbon.centerHeading,
        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding()
    )
}

@Composable
internal fun BoxScope.ExploreDebugOverlay(uiState: ExploreUiState) {
    if (uiState.simpleModeEnabled) return

    val hFov = uiState.cameraFov.horizontalDeg
    val vFov = uiState.cameraFov.verticalDeg
    Box(modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 120.dp)) {
        androidx.compose.foundation.layout.Column {
            uiState.debugSmoothedAzimuthDeg?.let { azimuth ->
                Text(text = "Az: ${"%.1f".format(azimuth)}°", color = NofARColors.ArAccent)
            }
            Text(text = "FOV: ${"%.1f".format(hFov)}° × ${"%.1f".format(vFov)}°", color = Color.White)
            if (uiState.visibleEntityCount > 0) {
                Text(text = "Visible: ${uiState.visibleEntityCount}", color = NofARColors.ArAccent)
            }
        }
    }
}

@Composable
internal fun BoxScope.ExploreExitButton(onNavigateBack: () -> Unit) {
    Box(
        modifier =
        Modifier
            .align(Alignment.BottomStart)
            .padding(start = 16.dp, bottom = 72.dp)
    ) {
        NofARIconActionButton(onClick = onNavigateBack) {
            Icon(Icons.Default.Close, contentDescription = "Exit Explore", tint = Color.White)
        }
    }
}

@Composable
internal fun BoxScope.ExploreSettingsButton(onNavigateToSettings: () -> Unit) {
    Box(
        modifier =
        Modifier
            .align(Alignment.BottomStart)
            .padding(start = 16.dp, bottom = 72.dp)
    ) {
        NofARIconActionButton(onClick = onNavigateToSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
        }
    }
}
