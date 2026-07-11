package com.nofar.feature.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nofar.core.designsystem.component.NofARPrimaryButton
import com.nofar.core.designsystem.component.NofARSecondaryOutlinedButton
import com.nofar.core.designsystem.theme.NofARColors
import com.nofar.core.ui.R

@Composable
fun ExploreDownloadPromptOverlay(
    proposalName: String,
    estimateDisplay: String,
    demTileCount: Int,
    errorMessage: String?,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier =
        modifier
            .fillMaxSize()
            .background(NofARColors.ArOverlayBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.explore_download_prompt_title),
                style = MaterialTheme.typography.titleMedium,
                color = NofARColors.TextPrimary,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
            Text(
                text =
                stringResource(
                    R.string.explore_download_prompt_message,
                    proposalName,
                    estimateDisplay,
                    demTileCount
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = NofARColors.TextPrimary,
                textAlign = TextAlign.Center
            )
            errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NofARColors.ArAccent,
                    textAlign = TextAlign.Center
                )
            }
            NofARPrimaryButton(
                text = stringResource(R.string.explore_download_map_data),
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth()
            )
            NofARSecondaryOutlinedButton(
                text = stringResource(R.string.explore_download_not_now),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ExploreDownloadProgressOverlay(progressPct: Int, modifier: Modifier = Modifier) {
    Box(
        modifier =
        modifier
            .fillMaxSize()
            .background(NofARColors.ArOverlayBackground.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            CircularProgressIndicator(
                progress = { progressPct / 100f },
                color = NofARColors.ArAccent
            )
            Text(
                text = stringResource(R.string.explore_download_in_progress),
                style = MaterialTheme.typography.bodyLarge,
                color = NofARColors.TextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.explore_download_progress, progressPct),
                style = MaterialTheme.typography.bodyMedium,
                color = NofARColors.TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ExploreDownloadDismissedBanner(onShowDownloadPrompt: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = NofARColors.SurfaceVariant.copy(alpha = 0.92f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.explore_download_prompt_title),
                style = MaterialTheme.typography.bodyMedium,
                color = NofARColors.TextPrimary
            )
            NofARPrimaryButton(
                text = stringResource(R.string.explore_download_map_data_action),
                onClick = onShowDownloadPrompt,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ExploreWifiOnlyBlockedDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wi-Fi only downloads") },
        text = {
            Text(
                "Wi-Fi only downloads are enabled in Settings. Connect to Wi-Fi before downloading map data."
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
fun ExploreCellularWarningDialog(
    demTileCount: Int,
    estimateDisplay: String,
    onDownloadAnyway: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Large Download Warning") },
        text = {
            Text(
                "This region requires $demTileCount DEM tiles (~$estimateDisplay). " +
                    "Download over cellular or wait for Wi-Fi?"
            )
        },
        confirmButton = {
            TextButton(onClick = onDownloadAnyway) {
                Text("Download anyway", color = NofARColors.PrimaryYellow)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
