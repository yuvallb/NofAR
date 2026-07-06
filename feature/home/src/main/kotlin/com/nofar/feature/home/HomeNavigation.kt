package com.nofar.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import java.util.UUID

const val HOME_ROUTE = "home"

fun NavGraphBuilder.homeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToPrepare: (UUID?) -> Unit,
    onNavigateToExplore: (UUID) -> Unit
) {
    composable(route = HOME_ROUTE) {
        HomeScreen(
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToPrepare = onNavigateToPrepare,
            onNavigateToExplore = onNavigateToExplore
        )
    }
}
