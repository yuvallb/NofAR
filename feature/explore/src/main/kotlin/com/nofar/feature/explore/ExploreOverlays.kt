package com.nofar.feature.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import com.nofar.core.ui.permission.PermissionState
import com.nofar.core.visibility.ClusteredLabel

@Composable
fun ExploreCameraPermissionOverlay(permissionState: PermissionState, modifier: Modifier = Modifier) {
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
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = NofARColors.ArAccent
            )
            Text(
                text = stringResource(R.string.explore_camera_permission_rationale),
                style = MaterialTheme.typography.bodyLarge,
                color = NofARColors.TextPrimary,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
            NofARSecondaryOutlinedButton(
                text = stringResource(R.string.explore_open_settings),
                onClick = permissionState.openAppSettings,
                modifier = Modifier.fillMaxWidth()
            )
            NofARPrimaryButton(
                text = stringResource(R.string.explore_grant_camera),
                onClick = permissionState.requestCamera,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ExploreWaitingForGpsOverlay(modifier: Modifier = Modifier) {
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
            CircularProgressIndicator(color = NofARColors.ArAccent)
            Text(
                text = stringResource(R.string.explore_waiting_for_gps),
                style = MaterialTheme.typography.bodyLarge,
                color = NofARColors.TextPrimary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ExploreCompassUnavailableOverlay(onExit: () -> Unit, modifier: Modifier = Modifier) {
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
                text = stringResource(R.string.explore_compass_unavailable),
                style = MaterialTheme.typography.bodyLarge,
                color = NofARColors.TextPrimary,
                textAlign = TextAlign.Center
            )
            NofARPrimaryButton(
                text = stringResource(R.string.explore_exit_to_home),
                onClick = onExit,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ExploreRegionDataErrorOverlay(onExit: () -> Unit, modifier: Modifier = Modifier) {
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
                text = stringResource(R.string.explore_region_data_missing),
                style = MaterialTheme.typography.bodyLarge,
                color = NofARColors.ArAccent,
                textAlign = TextAlign.Center
            )
            NofARPrimaryButton(
                text = stringResource(R.string.explore_exit_to_home),
                onClick = onExit,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ExplorePartialRegionBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = NofARColors.WarningBanner.copy(alpha = 0.92f)
    ) {
        Text(
            text = stringResource(R.string.explore_partial_region_warning),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = NofARColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ExploreLocationAccuracyBanner(accuracyMeters: Int, thresholdMeters: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = NofARColors.WarningBanner.copy(alpha = 0.92f)
    ) {
        Text(
            text =
            stringResource(
                R.string.explore_location_accuracy_degraded,
                accuracyMeters,
                thresholdMeters
            ),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = NofARColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ExploreRegionExitBanner(regionName: String, graceSecondsRemaining: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = NofARColors.WarningBanner.copy(alpha = 0.92f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = stringResource(R.string.explore_outside_region_title),
                style = MaterialTheme.typography.titleSmall,
                color = NofARColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text =
                stringResource(
                    R.string.explore_outside_region_message,
                    regionName,
                    graceSecondsRemaining
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = NofARColors.TextPrimary
            )
        }
    }
}

@Composable
fun ExploreGraceExpiredDialog(regionName: String, onExit: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(text = stringResource(R.string.explore_grace_expired_title))
        },
        text = {
            Text(text = stringResource(R.string.explore_grace_expired_message, regionName))
        },
        confirmButton = {
            TextButton(onClick = onExit) {
                Text(
                    text = stringResource(R.string.explore_return_to_home),
                    color = NofARColors.PrimaryYellow
                )
            }
        }
    )
}

@Composable
fun ExploreNoVisibleEntitiesHint(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.explore_no_visible_entities),
        modifier =
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = NofARColors.TextSecondary,
        textAlign = TextAlign.Center
    )
}

@Composable
fun ExploreOsmWatermark(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.explore_osm_watermark),
        modifier = modifier.padding(8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = NofARColors.TextSecondary.copy(alpha = 0.85f)
    )
}

@Composable
fun ExploreExpandedBucketDialog(cluster: ClusteredLabel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.explore_expanded_bucket_title)) },
        text = {
            LazyColumn {
                items(cluster.labels) { label ->
                    Text(
                        text = "${label.name} · ${label.distanceDisplay}",
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.explore_dismiss))
            }
        }
    )
}
