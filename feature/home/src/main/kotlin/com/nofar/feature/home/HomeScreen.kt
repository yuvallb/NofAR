package com.nofar.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nofar.core.designsystem.component.NofARHomeTopBar
import com.nofar.core.designsystem.component.NofARRegionCard
import com.nofar.core.designsystem.component.NofARRegionListDivider
import com.nofar.core.designsystem.component.NofARSecondaryOutlinedButton
import com.nofar.core.designsystem.component.NofARSectionHeader
import com.nofar.core.designsystem.component.NofARStorageSummary
import com.nofar.core.designsystem.component.RegionCardState
import com.nofar.core.designsystem.theme.NofARColors
import com.nofar.core.model.Region
import java.util.UUID

@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToPrepare: (UUID?) -> Unit,
    onNavigateToExplore: (UUID) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.navigateToExploreRegionId) {
        uiState.navigateToExploreRegionId?.let { regionId ->
            onNavigateToExplore(regionId)
            viewModel.onExploreNavigationHandled()
        }
    }

    HomeScreenContent(
        uiState = uiState,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToPrepare = onNavigateToPrepare,
        onEnterExplore = viewModel::onEnterExploreClicked,
        onDelete = viewModel::onDeleteClicked,
        modifier = modifier
    )

    HomeScreenDialogs(
        uiState = uiState,
        viewModel = viewModel
    )
}

@Composable
private fun HomeScreenContent(
    uiState: HomeUiState,
    onNavigateToSettings: () -> Unit,
    onNavigateToPrepare: (UUID?) -> Unit,
    onEnterExplore: (UUID) -> Unit,
    onDelete: (UUID) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        NofARHomeTopBar(
            onSettingsClick = onNavigateToSettings,
            settingsIcon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = NofARColors.PrimaryYellow
                )
            }
        )
        NofARSecondaryOutlinedButton(
            text = "+ ADD REGION",
            onClick = { onNavigateToPrepare(null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        NofARSectionHeader(title = "LOCAL REGIONS")
        HomeRegionList(
            regions = uiState.regions,
            onEnterExplore = onEnterExplore,
            onPrepare = { regionId -> onNavigateToPrepare(regionId) },
            onDelete = onDelete,
            modifier = Modifier.weight(1f)
        )
        NofARStorageSummary(
            demCacheBytes = uiState.demCacheBytes,
            entitiesDbBytes = uiState.entitiesDbBytes,
            freeSpaceBytes = uiState.freeSpaceBytes,
            onAdvancedStorageClick = onNavigateToSettings
        )
    }
}

@Composable
private fun HomeRegionList(
    regions: List<RegionCardState>,
    onEnterExplore: (UUID) -> Unit,
    onPrepare: (UUID) -> Unit,
    onDelete: (UUID) -> Unit,
    modifier: Modifier = Modifier
) {
    if (regions.isEmpty()) {
        Text(
            text = "No regions yet. Tap + ADD REGION to download map data for an area.",
            modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = NofARColors.TextSecondary
        )
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(regions, key = { it.region.id }) { cardState ->
            NofARRegionCard(
                state = cardState,
                onEnterExplore = onEnterExplore,
                onPrepare = onPrepare,
                onDelete = onDelete,
                deleteIcon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete region",
                        tint = NofARColors.TextSecondary
                    )
                }
            )
            NofARRegionListDivider()
        }
    }
}

@Composable
private fun HomeScreenDialogs(uiState: HomeUiState, viewModel: HomeViewModel) {
    uiState.deleteConfirmRegion?.let { region ->
        DeleteRegionDialog(
            region = region,
            onConfirm = viewModel::confirmDeleteRegion,
            onDismiss = viewModel::dismissDeleteRegion
        )
    }
    uiState.overlappingRegionsDialog?.let { regions ->
        OverlappingRegionsDialog(
            regions = regions,
            onSelect = viewModel::onOverlappingRegionSelected,
            onDismiss = viewModel::dismissOverlappingRegionsDialog
        )
    }
}

@Composable
private fun DeleteRegionDialog(region: Region, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${region.name}?") },
        text = { Text("This removes the region and any data not shared with other regions.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("DELETE", color = NofARColors.ErrorDestructive)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL")
            }
        }
    )
}

@Composable
private fun OverlappingRegionsDialog(regions: List<Region>, onSelect: (UUID) -> Unit, onDismiss: () -> Unit) {
    var selectedId by remember(regions) { mutableStateOf(regions.first().id) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Multiple regions cover this area") },
        text = {
            Column {
                Text("Which data context would you like to use?")
                Spacer(modifier = Modifier.height(8.dp))
                regions.forEach { region ->
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedId == region.id,
                            onClick = { selectedId = region.id },
                            colors = RadioButtonDefaults.colors(selectedColor = NofARColors.PrimaryYellow)
                        )
                        Text(text = region.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(selectedId) }) {
                Text("CONFIRM")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL")
            }
        }
    )
}
