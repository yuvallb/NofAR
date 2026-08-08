package com.nofar.feature.prepare

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

fun NavGraphBuilder.prepareScreen(onNavigateBack: () -> Unit, onNavigateToSettings: () -> Unit = {}) {
    composable(
        route = PREPARE_ROUTE_WITH_ARG,
        arguments =
        listOf(
            navArgument("regionId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument("centerLat") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument("centerLon") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument("radiusM") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument("name") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
    ) {
        PrepareScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToSettings = onNavigateToSettings
        )
    }
}
