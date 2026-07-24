package com.nofar.feature.explore

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nofar.core.designsystem.component.NofARArLabel
import com.nofar.core.designsystem.component.NofARCompassRibbon
import com.nofar.core.designsystem.component.NofARExploreAltitudeReadout
import com.nofar.core.designsystem.component.NofARExploreBottomHudHeight
import com.nofar.core.designsystem.component.NofARIconActionButton
import com.nofar.core.designsystem.component.NofARLocationAccuracyBadge
import com.nofar.core.designsystem.theme.NofARColors
import com.nofar.core.model.LocationAccessState
import com.nofar.core.ui.permission.PermissionState
import com.nofar.core.ui.permission.rememberNofARPermissionState
import com.nofar.feature.explore.BuildConfig

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
        onDismissWifiOnlyBlocked = viewModel::dismissWifiOnlyBlocked
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
            modifier = Modifier.fillMaxSize(),
            onHiddenCountClick = onHiddenCountClick
        )
    }
}

@Composable
internal fun BoxScope.ExploreCompassRibbon(uiState: ExploreUiState) {
    val orientedFov =
        uiState.cameraFov.orientedForScreen(
            screenWidthPx = uiState.screenWidthPx,
            screenHeightPx = uiState.screenHeightPx
        )
    NofARCompassRibbon(
        bearingDeg = uiState.compassBearingDeg,
        horizontalFovDeg = orientedFov.horizontalDeg,
        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding()
    )
}

private val ExploreChromePortraitStartInset = 16.dp
private val ExploreChromeLandscapeStartInset = 72.dp

@Composable
internal fun exploreChromeStartInset(): Dp {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    return if (isLandscape) ExploreChromeLandscapeStartInset else ExploreChromePortraitStartInset
}

@Composable
internal fun exploreChromeHorizontalPadding(): PaddingValues {
    val start = exploreChromeStartInset()
    return PaddingValues(start = start, end = ExploreChromePortraitStartInset)
}

@Composable
internal fun BoxScope.ExploreExpertBottomControls(
    uiState: ExploreUiState,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier =
        modifier
            .align(Alignment.BottomStart)
            .padding(
                start = exploreChromeStartInset(),
                bottom = NofARExploreBottomHudHeight + 8.dp
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        if (BuildConfig.DEBUG) {
            ExploreDebugReadout(uiState = uiState)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NofARExploreAltitudeReadout(altitude = uiState.altitude)
            NofARLocationAccuracyBadge(
                accuracyMeters = uiState.locationAccuracyMeters,
                isDegraded = uiState.locationAccuracyDegraded
            )
        }
        NofARIconActionButton(onClick = onNavigateBack) {
            Icon(Icons.Default.Close, contentDescription = "Exit Explore", tint = Color.White)
        }
    }
}

@Composable
private fun ExploreDebugReadout(uiState: ExploreUiState) {
    val hFov = uiState.cameraFov.horizontalDeg
    val vFov = uiState.cameraFov.verticalDeg
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        uiState.debugSmoothedAzimuthDeg?.let { azimuth ->
            Text(text = "Az: ${"%.1f".format(azimuth)}°", color = NofARColors.ArAccent)
        }
        Text(text = "FOV: ${"%.1f".format(hFov)}° × ${"%.1f".format(vFov)}°", color = Color.White)
        if (uiState.visibleEntityCount > 0) {
            Text(text = "Visible: ${uiState.visibleEntityCount}", color = NofARColors.ArAccent)
        }
    }
}

@Composable
internal fun BoxScope.ExploreExitButton(onNavigateBack: () -> Unit) {
    Box(
        modifier =
        Modifier
            .align(Alignment.BottomStart)
            .padding(start = exploreChromeStartInset(), bottom = 16.dp)
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
