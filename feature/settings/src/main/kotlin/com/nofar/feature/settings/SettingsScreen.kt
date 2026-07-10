package com.nofar.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nofar.core.common.locale.LocaleApplier
import com.nofar.core.designsystem.component.NofARBackTopBar
import com.nofar.core.designsystem.theme.NofARColors

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = LocalContext.current as? ComponentActivity

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.refreshStats()
                    viewModel.refreshAppLanguage()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val context = LocalContext.current

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.asString(context)?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissSnackbar()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            NofARBackTopBar(
                title = stringResource(R.string.settings_title),
                onNavigateBack = onNavigateBack,
                navigationIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.settings_back),
                        tint = NofARColors.PrimaryYellow
                    )
                }
            )
            SettingsContent(
                uiState = uiState,
                onAppLanguageChanged = { language ->
                    LocaleApplier.apply(language)
                    viewModel.onAppLanguageChanged(language)
                    activity?.recreate()
                },
                onWifiOnlyChanged = viewModel::onWifiOnlyDownloadsChanged,
                onEvictionThresholdChanged = viewModel::onEvictionThresholdChanged,
                onShowPurgeConfirm = viewModel::showPurgeConfirm,
                onShowRawSensorChanged = viewModel::onShowRawSensorOverlayChanged,
                onKeepRawGeoTiffChanged = viewModel::onKeepRawGeoTiffChanged
            )
        }
    }

    SettingsDialogs(uiState = uiState, viewModel = viewModel)
}
