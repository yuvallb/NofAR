package com.nofar.feature.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
internal fun SettingsDialogs(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    if (uiState.showPurgeConfirm) {
        PurgeConfirmDialog(
            onConfirm = viewModel::confirmPurgeUnusedTiles,
            onDismiss = viewModel::dismissPurgeConfirm
        )
    }
    if (uiState.showForceEvictConfirm) {
        ForceEvictConfirmDialog(
            onConfirm = viewModel::confirmForceEviction,
            onDismiss = viewModel::dismissForceEvictConfirm
        )
    }
}

@Composable
private fun PurgeConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_purge_title)) },
        text = {
            Text(stringResource(R.string.settings_purge_message))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.settings_proceed))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        }
    )
}

@Composable
private fun ForceEvictConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_force_evict_title)) },
        text = {
            Text(stringResource(R.string.settings_force_evict_message))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.settings_delete_oldest_tiles))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        }
    )
}
