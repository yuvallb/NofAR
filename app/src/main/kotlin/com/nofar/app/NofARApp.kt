package com.nofar.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nofar.core.designsystem.theme.NofARColors
import com.nofar.core.ui.R

@Composable
fun NofARApp(viewModel: AppStartupViewModel = hiltViewModel()) {
    val startupState by viewModel.startupState.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = NofARColors.Background
    ) {
        when (val state = startupState) {
            AppStartupState.Loading -> Unit
            is AppStartupState.Ready -> {
                val navController = androidx.navigation.compose.rememberNavController()
                NofARNavHost(
                    navController = navController,
                    simpleModeEnabled = state.simpleModeEnabled
                )
                if (state.showDemUpgradeMessage) {
                    DemUpgradeDialog(onDismiss = viewModel::onDemUpgradeMessageDismissed)
                }
            }
        }
    }
}

@Composable
private fun DemUpgradeDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dem_upgrade_title)) },
        text = {
            Text(
                text = stringResource(R.string.dem_upgrade_message),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.explore_dismiss))
            }
        }
    )
}
