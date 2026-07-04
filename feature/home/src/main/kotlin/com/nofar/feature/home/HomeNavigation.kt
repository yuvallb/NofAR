package com.nofar.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val HOME_ROUTE = "home"

fun NavGraphBuilder.homeScreen(onNavigateToSettings: () -> Unit) {
    composable(route = HOME_ROUTE) {
        HomeScreen(onNavigateToSettings = onNavigateToSettings)
    }
}
