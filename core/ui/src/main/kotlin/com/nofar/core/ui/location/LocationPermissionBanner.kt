package com.nofar.core.ui.location

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nofar.core.designsystem.component.NofARSecondaryOutlinedButton
import com.nofar.core.designsystem.theme.NofARColors
import com.nofar.core.model.LocationAccessState
import com.nofar.core.ui.R
import com.nofar.core.ui.permission.PermissionState

@Composable
fun LocationPermissionBanner(
    permissionState: PermissionState,
    waitingForGpsFix: Boolean,
    modifier: Modifier = Modifier,
    onRequestPermission: () -> Unit = permissionState.requestFineLocation
) {
    when {
        permissionState.locationAccessState == LocationAccessState.NOT_REQUESTED -> {
            LocationRationaleContent(
                modifier = modifier,
                onRequestPermission = onRequestPermission
            )
        }
        permissionState.locationAccessState == LocationAccessState.DENIED ||
            permissionState.locationAccessState == LocationAccessState.DENIED_PERMANENTLY -> {
            LocationDeniedContent(
                permissionState = permissionState,
                modifier = modifier,
                onRequestPermission = onRequestPermission
            )
        }
        permissionState.fineLocationGranted && waitingForGpsFix -> {
            WaitingForGpsBanner(modifier = modifier)
        }
    }
}

@Composable
private fun LocationRationaleContent(modifier: Modifier = Modifier, onRequestPermission: () -> Unit) {
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.permission_rationale_location),
            style = MaterialTheme.typography.bodyMedium,
            color = NofARColors.TextSecondary
        )
        NofARSecondaryOutlinedButton(
            text = stringResource(R.string.location_grant_permission),
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }
}

@Composable
private fun LocationDeniedContent(
    permissionState: PermissionState,
    modifier: Modifier = Modifier,
    onRequestPermission: () -> Unit
) {
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text =
            if (permissionState.locationAccessState == LocationAccessState.DENIED_PERMANENTLY) {
                stringResource(R.string.location_permission_denied_permanent)
            } else {
                stringResource(R.string.location_permission_denied)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = NofARColors.TextSecondary
        )
        if (permissionState.locationAccessState == LocationAccessState.DENIED_PERMANENTLY) {
            TextButton(onClick = permissionState.openAppSettings) {
                Text(
                    text = stringResource(R.string.location_open_settings),
                    color = NofARColors.PrimaryYellow
                )
            }
        } else {
            NofARSecondaryOutlinedButton(
                text = stringResource(R.string.location_grant_permission),
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun WaitingForGpsBanner(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.location_waiting_for_fix),
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = NofARColors.TextSecondary
    )
}
