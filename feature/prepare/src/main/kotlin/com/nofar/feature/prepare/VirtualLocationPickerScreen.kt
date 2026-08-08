package com.nofar.feature.prepare

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nofar.core.designsystem.component.NofARBackTopBar
import com.nofar.core.designsystem.component.NofARPrimaryButton
import com.nofar.core.designsystem.theme.NofARColors
import com.nofar.core.ui.permission.rememberNofARPermissionState
import org.osmdroid.config.Configuration

@Composable
fun VirtualLocationPickerScreen(
    onNavigateBack: () -> Unit,
    onContinueToVirtualExplore: (VirtualLocationSelection) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VirtualLocationPickerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionState = rememberNofARPermissionState()

    DisposableEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        onDispose { }
    }

    LaunchedEffect(permissionState.locationAccessState) {
        viewModel.onLocationPermissionChanged(permissionState.locationAccessState)
    }

    VirtualLocationPickerScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onMapTap = viewModel::onMapTap,
        onContinue = { viewModel.currentSelection()?.let(onContinueToVirtualExplore) },
        modifier = modifier
    )
}

@Composable
private fun VirtualLocationPickerScreenContent(
    uiState: VirtualLocationPickerUiState,
    onNavigateBack: () -> Unit,
    onMapTap: (Double, Double) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        NofARBackTopBar(
            title = "Explore another location",
            onNavigateBack = onNavigateBack,
            navigationIcon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = NofARColors.PrimaryYellow
                )
            }
        )
        VirtualLocationPickerMapSection(
            uiState = uiState,
            onMapTap = onMapTap,
            modifier = Modifier.weight(1f)
        )
        VirtualLocationPickerFooter(
            uiState = uiState,
            onContinue = onContinue
        )
    }
}

@Composable
private fun VirtualLocationPickerMapSection(
    uiState: VirtualLocationPickerUiState,
    onMapTap: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        val lat = uiState.selectedLat
        val lon = uiState.selectedLon
        if (lat != null && lon != null) {
            PreparePointPickerMap(
                selectedLat = lat,
                selectedLon = lon,
                downloadedRegions = uiState.eligibleRegions,
                mapRecenterNonce = uiState.mapRecenterNonce,
                onMapTap = onMapTap,
                visibilityMask = uiState.visibilityMask,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ColumnScope.VirtualLocationPickerFooter(uiState: VirtualLocationPickerUiState, onContinue: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        uiState.helperMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = NofARColors.TextCaption
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(
            text = "© OpenStreetMap contributors",
            style = MaterialTheme.typography.labelSmall,
            color = NofARColors.TextCaption
        )
        Spacer(modifier = Modifier.height(12.dp))
        NofARPrimaryButton(
            text = "CONTINUE TO EXPLORE",
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.selectionValid
        )
    }
}
