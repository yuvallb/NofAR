package com.nofar.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nofar.core.designsystem.theme.NofARColors

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
            }
        }
    }
}
