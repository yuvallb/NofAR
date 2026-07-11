package com.nofar.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val SETTINGS_ROUTE = "settings"

fun NavGraphBuilder.settingsScreen(onNavigateBack: () -> Unit, onSimpleModeChanged: (Boolean) -> Unit) {
    composable(route = SETTINGS_ROUTE) {
        SettingsScreen(
            onNavigateBack = onNavigateBack,
            onSimpleModeChanged = onSimpleModeChanged
        )
    }
}
