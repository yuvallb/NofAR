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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.nofar.core.designsystem.component.NofARPrimaryButton
import com.nofar.core.designsystem.component.NofARRegionCard
import com.nofar.core.designsystem.component.NofARRegionListDivider
import com.nofar.core.designsystem.component.NofARSecondaryOutlinedButton
import com.nofar.core.designsystem.component.NofARSectionHeader
import com.nofar.core.designsystem.component.NofARStorageSummary
import com.nofar.core.designsystem.component.RegionCardState
import com.nofar.core.designsystem.theme.NofARColors
import com.nofar.core.model.Region
import com.nofar.core.ui.location.LocationPermissionBanner
import com.nofar.core.ui.permission.PermissionState
import com.nofar.core.ui.permission.rememberNofARPermissionState
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
    val permissionState = rememberNofARPermissionState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(permissionState.locationAccessState) {
        viewModel.onLocationPermissionChanged(permissionState.locationAccessState)
    }

    LaunchedEffect(uiState.navigateToExploreRegionId) {
        uiState.navigateToExploreRegionId?.let { regionId ->
            onNavigateToExplore(regionId)
            viewModel.onExploreUiAction(ExploreUiAction.NavigationHandled)
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onSnackbarShown()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        HomeScreenContent(
            uiState = uiState,
            permissionState = permissionState,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToPrepare = onNavigateToPrepare,
            onGlobalEnterExplore = viewModel::onGlobalEnterExploreClicked,
            onEnterExplore = viewModel::onEnterExploreClicked,
            onDelete = viewModel::onDeleteClicked,
            modifier = Modifier.padding(padding)
        )
    }

    HomeScreenDialogs(
        uiState = uiState,
        viewModel = viewModel
    )
}

@Composable
private fun HomeScreenContent(
    uiState: HomeUiState,
    permissionState: PermissionState,
    onNavigateToSettings: () -> Unit,
    onNavigateToPrepare: (UUID?) -> Unit,
    onGlobalEnterExplore: () -> Unit,
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
        LocationPermissionBanner(
            permissionState = permissionState,
            waitingForGpsFix = uiState.waitingForGpsFix
        )
        NofARSecondaryOutlinedButton(
            text = "+ ADD REGION",
            onClick = { onNavigateToPrepare(null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        GlobalEnterExploreSection(
            enabled = uiState.enterExploreEnabled,
            onClick = onGlobalEnterExplore
        )
        NofARSectionHeader(title = "LOCAL REGIONS")
        HomeRegionList(
            regions = uiState.regions,
            onEnterExplore = onEnterExplore,
            onPrepare = { regionId -> onNavigateToPrepare(regionId) },
            onAddRegion = { onNavigateToPrepare(null) },
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
private fun GlobalEnterExploreSection(enabled: Boolean, onClick: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        NofARPrimaryButton(
            text = "ENTER EXPLORE",
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        )
        if (!enabled) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Move inside a ready region to explore the horizon.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = NofARColors.TextCaption
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun HomeRegionList(
    regions: List<RegionCardState>,
    onEnterExplore: (UUID) -> Unit,
    onPrepare: (UUID) -> Unit,
    onAddRegion: () -> Unit,
    onDelete: (UUID) -> Unit,
    modifier: Modifier = Modifier
) {
    if (regions.isEmpty()) {
        Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "No regions yet. Download map data for an area to get started.",
                color = NofARColors.TextSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            NofARPrimaryButton(
                text = "ADD REGION",
                onClick = onAddRegion,
                modifier = Modifier.fillMaxWidth()
            )
        }
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
            initialSelectionId =
            HomeRegionLogic.defaultOverlapSelection(
                regions,
                uiState.lastSelectedOverlapRegionId
            ),
            onSelect = viewModel::onOverlappingRegionSelected,
            onDismiss = { viewModel.onExploreUiAction(ExploreUiAction.DismissOverlap) }
        )
    }
}

@Composable
private fun DeleteRegionDialog(region: Region, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${region.name}?") },
        text = {
            Text(
                "This removes the region and any entities or DEM tiles not shared with other regions."
            )
        },
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
private fun OverlappingRegionsDialog(
    regions: List<Region>,
    initialSelectionId: UUID,
    onSelect: (UUID) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedId by remember(regions, initialSelectionId) { mutableStateOf(initialSelectionId) }

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
