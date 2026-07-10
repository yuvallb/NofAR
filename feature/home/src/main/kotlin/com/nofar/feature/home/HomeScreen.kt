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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.nofar.core.model.LocationAccessState
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

    HomePermissionEffects(permissionState = permissionState)

    LaunchedEffect(uiState.navigateToExploreRegionId) {
        uiState.navigateToExploreRegionId?.let { regionId ->
            onNavigateToExplore(regionId)
            viewModel.onExploreUiAction(ExploreUiAction.NavigationHandled)
        }
    }

    val context = LocalContext.current

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.asString(context)?.let { message ->
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
private fun HomePermissionEffects(permissionState: PermissionState) {
    LaunchedEffect(permissionState.locationAccessState) {
        if (permissionState.locationAccessState == LocationAccessState.NOT_REQUESTED) {
            permissionState.requestFineLocation()
        }
    }
}

@Composable
private fun HomeScreenContent(
    uiState: HomeUiState,
    permissionState: PermissionState,
    onNavigateToSettings: () -> Unit,
    onNavigateToPrepare: (UUID?) -> Unit,
    onGlobalEnterExplore: () -> Unit,
    onDelete: (UUID) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        NofARHomeTopBar(
            onSettingsClick = onNavigateToSettings,
            settingsIcon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.home_settings),
                    tint = NofARColors.PrimaryYellow
                )
            }
        )
        LocationPermissionBanner(
            permissionState = permissionState,
            waitingForGpsFix = uiState.waitingForGpsFix
        )
        GlobalEnterExploreSection(
            enabled = uiState.enterExploreEnabled,
            onClick = onGlobalEnterExplore
        )
        NofARSecondaryOutlinedButton(
            text = stringResource(R.string.home_add_region),
            onClick = { onNavigateToPrepare(null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        NofARSectionHeader(title = stringResource(R.string.home_local_regions))
        HomeRegionList(
            regions = uiState.regions,
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
            text = stringResource(R.string.home_enter_explore),
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        )
        if (!enabled) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_enter_explore_hint),
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
    onPrepare: (UUID) -> Unit,
    onAddRegion: () -> Unit,
    onDelete: (UUID) -> Unit,
    modifier: Modifier = Modifier
) {
    if (regions.isEmpty()) {
        Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.home_no_regions),
                color = NofARColors.TextSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            NofARPrimaryButton(
                text = stringResource(R.string.home_add_region_button),
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
                onPrepare = onPrepare,
                onDelete = onDelete,
                deleteIcon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.home_delete_region),
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
        title = { Text(stringResource(R.string.home_delete_title, region.name)) },
        text = {
            Text(stringResource(R.string.home_delete_message))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.home_delete_confirm), color = NofARColors.ErrorDestructive)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.home_cancel))
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
        title = { Text(stringResource(R.string.home_overlap_title)) },
        text = {
            Column {
                Text(stringResource(R.string.home_overlap_message))
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
                Text(stringResource(R.string.home_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.home_cancel))
            }
        }
    )
}
