package com.nofar.feature.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

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
private fun ForceEvictConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cache still over limit") },
        text = {
            Text(
                "Unused tiles were removed but the DEM cache still exceeds your limit. " +
                    "Delete oldest tiles (including those used by regions) to free space? " +
                    "Affected regions will be marked partial and may lose terrain accuracy."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("DELETE OLDEST TILES")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL")
            }
        }
    )
}
