package com.nofar.feature.explore

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nofar.core.designsystem.component.ArLabel
import com.nofar.core.designsystem.component.NofARArLabel
import com.nofar.core.designsystem.component.NofARCompassRibbon
import com.nofar.core.designsystem.component.NofARExploreBottomHud
import com.nofar.core.designsystem.component.NofARExploreCameraPlaceholder
import com.nofar.core.designsystem.component.NofARExploreSideControls
import com.nofar.core.designsystem.component.NofARIconActionButton
import com.nofar.core.designsystem.theme.NofARColors
import com.nofar.core.model.AppConfig
import com.nofar.core.ui.compass.CompassCalibrationHint
import com.nofar.core.ui.location.LocationPermissionBanner
import com.nofar.core.ui.permission.rememberNofARPermissionState
import com.nofar.feature.explore.BuildConfig

@Composable
fun ExploreScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExploreViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionState = rememberNofARPermissionState()

    LaunchedEffect(permissionState.locationAccessState) {
        viewModel.onLocationPermissionChanged(permissionState.locationAccessState)
    }

    Box(modifier = modifier.fillMaxSize()) {
        NofARExploreCameraPlaceholder()
        ExploreCompassRibbon(uiState = uiState)
        CompassCalibrationHint(
            calibrationState = uiState.calibrationState,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp)
        )
        LocationPermissionBanner(
            permissionState = permissionState,
            waitingForGpsFix = uiState.waitingForGpsFix,
            modifier = Modifier.align(Alignment.Center)
        )
        ExploreDemoLabels()
        ExploreSideControlsPanel()
        NofARExploreBottomHud(
            altitudeM = uiState.altitudeM ?: "—",
            maxRangeKm = AppConfig.REGION_RADIUS_MAX_KM.toInt(),
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        if (BuildConfig.DEBUG) {
            ExploreOrientationDebugOverlay(
                uiState = uiState,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 120.dp)
            )
        }
        ExploreExitButton(onNavigateBack = onNavigateBack)
    }
}

@Composable
private fun BoxScope.ExploreCompassRibbon(uiState: ExploreUiState) {
    NofARCompassRibbon(
        headings = uiState.compassRibbon.headings,
        centerHeading = uiState.compassRibbon.centerHeading,
        modifier = Modifier.align(Alignment.TopCenter)
    )
}

@Composable
private fun ExploreOrientationDebugOverlay(uiState: ExploreUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        uiState.debugSmoothedAzimuthDeg?.let { smoothed ->
            Text(
                text = "Az smoothed: ${"%.1f".format(smoothed)}°",
                color = NofARColors.ArAccent
            )
        }
        uiState.debugRawAzimuthDeg?.let { raw ->
            Text(
                text = "Az raw: ${"%.1f".format(raw)}°",
                color = Color.White
            )
        }
        if (uiState.visibleEntityCount > 0) {
            Text(
                text = "Visible: ${uiState.visibleEntityCount}",
                color = NofARColors.ArAccent
            )
        }
    }
}

@Composable
private fun BoxScope.ExploreDemoLabels() {
    exploreDemoLabels().forEach { label ->
        NofARArLabel(
            label = label,
            modifier =
            Modifier
                .align(Alignment.TopStart)
                .padding(
                    start = (label.xFraction * 280).dp,
                    top = (label.yFraction * 400).dp
                )
        )
    }
}

@Composable
private fun BoxScope.ExploreSideControlsPanel() {
    NofARExploreSideControls(
        modifier = Modifier.align(Alignment.CenterEnd),
        targetIcon = {
            Icon(Icons.Default.Explore, contentDescription = "Center", tint = Color.White)
        },
        layersIcon = {
            Icon(Icons.Default.Landscape, contentDescription = "Layers", tint = Color.White)
        },
        filterIcon = {
            Icon(Icons.Default.Place, contentDescription = "Filter", tint = Color.White)
        }
    )
}

@Composable
private fun BoxScope.ExploreExitButton(onNavigateBack: () -> Unit) {
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

private fun exploreDemoLabels(): List<ArLabel> = listOf(
    ArLabel(
        name = "MERON",
        elevationM = 1208,
        distanceKm = "4.2",
        isPeak = true,
        xFraction = 0.72f,
        yFraction = 0.28f,
        hiddenCount = 3
    ),
    ArLabel(
        name = "TIBERIAS",
        elevationM = null,
        distanceKm = "11.5",
        isPeak = false,
        xFraction = 0.18f,
        yFraction = 0.45f
    )
)
