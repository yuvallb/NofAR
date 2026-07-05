package com.nofar.feature.explore

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nofar.core.designsystem.component.ArLabel
import com.nofar.core.designsystem.component.NofARArLabel
import com.nofar.core.designsystem.component.NofARCompassRibbon
import com.nofar.core.designsystem.component.NofARExploreBottomHud
import com.nofar.core.designsystem.component.NofARExploreCameraPlaceholder
import com.nofar.core.designsystem.component.NofARExploreSideControls
import com.nofar.core.designsystem.component.NofARIconActionButton
import com.nofar.core.model.AppConfig

@Composable
fun ExploreScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        NofARExploreCameraPlaceholder()
        ExploreCompassRibbon()
        ExploreDemoLabels()
        ExploreSideControlsPanel()
        NofARExploreBottomHud(
            altitudeM = "682",
            maxRangeKm = AppConfig.REGION_RADIUS_MAX_KM.toInt(),
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        ExploreExitButton(onNavigateBack = onNavigateBack)
    }
}

@Composable
private fun BoxScope.ExploreCompassRibbon() {
    NofARCompassRibbon(
        headings = listOf("NW", "330", "345", "N", "15", "30", "NE"),
        centerHeading = "N",
        modifier = Modifier.align(Alignment.TopCenter)
    )
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
