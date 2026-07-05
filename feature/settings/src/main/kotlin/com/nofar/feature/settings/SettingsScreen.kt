package com.nofar.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nofar.core.designsystem.component.NofARBackTopBar
import com.nofar.core.designsystem.theme.NofARColors
import com.nofar.core.designsystem.util.NofARFormatters
import com.nofar.core.model.AppConfig

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        NofARBackTopBar(
            title = "Advanced Storage",
            onNavigateBack = onNavigateBack,
            navigationIcon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = NofARColors.PrimaryYellow
                )
            }
        )
        SettingsStorageContent(
            uiState = uiState,
            onEvictionThresholdChanged = viewModel::onEvictionThresholdChanged,
            onShowPurgeConfirm = viewModel::showPurgeConfirm
        )
    }

    SettingsDialogs(uiState = uiState, viewModel = viewModel)
}

@Composable
private fun SettingsStorageContent(
    uiState: SettingsUiState,
    onEvictionThresholdChanged: (Float) -> Unit,
    onShowPurgeConfirm: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        SettingsLiveMetrics(uiState = uiState)
        Spacer(modifier = Modifier.height(24.dp))
        SettingsEvictionThreshold(
            thresholdMb = uiState.evictionThresholdMb,
            onThresholdChanged = onEvictionThresholdChanged
        )
        Spacer(modifier = Modifier.height(24.dp))
        TextButton(onClick = onShowPurgeConfirm) {
            Text("CLEAR UNUSED DEM TILES", color = NofARColors.PrimaryYellow)
        }
        Spacer(modifier = Modifier.height(32.dp))
        SettingsAttributions()
    }
}

@Composable
private fun SettingsLiveMetrics(uiState: SettingsUiState) {
    Text(
        text = "LIVE METRICS",
        style = MaterialTheme.typography.labelMedium,
        color = NofARColors.TextCaption
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Total Elevation Data: ${NofARFormatters.formatMegabytes(uiState.demCacheBytes)}",
        style = MaterialTheme.typography.bodyMedium,
        color = NofARColors.TextPrimary
    )
    val entityLabel = NofARFormatters.formatCount(uiState.entityRowCount)
    Text(
        text = "Total Entity Database Entries: $entityLabel rows",
        style = MaterialTheme.typography.bodyMedium,
        color = NofARColors.TextPrimary
    )
}

@Composable
private fun SettingsEvictionThreshold(thresholdMb: Float, onThresholdChanged: (Float) -> Unit) {
    Text(
        text = "EVICTION THRESHOLD",
        style = MaterialTheme.typography.labelMedium,
        color = NofARColors.TextCaption
    )
    Text(
        text = "LRU limit: ${thresholdMb.toInt()} MB",
        style = MaterialTheme.typography.bodySmall,
        color = NofARColors.TextSecondary
    )
    Slider(
        value = thresholdMb,
        onValueChange = onThresholdChanged,
        valueRange = 100f..2_048f,
        steps = 19,
        colors =
        SliderDefaults.colors(
            thumbColor = NofARColors.PrimaryYellow,
            activeTrackColor = NofARColors.PrimaryYellow,
            inactiveTrackColor = NofARColors.SurfaceVariant
        )
    )
    val defaultMb = AppConfig.DEM_CACHE_DEFAULT_LIMIT_BYTES / (1024 * 1024)
    Text(
        text = "Default: $defaultMb MB",
        style = MaterialTheme.typography.bodySmall,
        color = NofARColors.TextCaption
    )
}

@Composable
private fun SettingsAttributions() {
    Text(
        text = "ATTRIBUTIONS",
        style = MaterialTheme.typography.labelMedium,
        color = NofARColors.TextCaption
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "© OpenStreetMap contributors",
        style = MaterialTheme.typography.bodySmall,
        color = NofARColors.TextSecondary
    )
    Text(
        text = "Copernicus DEM, ESA / Airbus",
        style = MaterialTheme.typography.bodySmall,
        color = NofARColors.TextSecondary
    )
}

@Composable
private fun SettingsDialogs(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    if (uiState.showPurgeConfirm) {
        PurgeConfirmDialog(
            onConfirm = viewModel::confirmPurgeUnusedTiles,
            onDismiss = viewModel::dismissPurgeConfirm
        )
    }
    uiState.purgeResultMessage?.let { message ->
        PurgeResultDialog(message = message, onDismiss = viewModel::dismissPurgeResult)
    }
}

@Composable
private fun PurgeConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear unused DEM tiles?") },
        text = {
            Text(
                "This will remove all elevation raster blocks (.bin) that are no longer " +
                    "associated with your active circular regions. Saved regions will not be affected. Proceed?"
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("PROCEED")
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
private fun PurgeResultDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Purge complete") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}
