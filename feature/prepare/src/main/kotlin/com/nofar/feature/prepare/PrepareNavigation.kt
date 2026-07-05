package com.nofar.feature.prepare

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

const val PREPARE_ROUTE = "prepare"
const val PREPARE_ROUTE_WITH_ARG = "prepare?regionId={regionId}"

fun NavGraphBuilder.prepareScreen(onNavigateBack: () -> Unit) {
    composable(
        route = PREPARE_ROUTE_WITH_ARG,
        arguments =
        listOf(
            navArgument("regionId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
    ) {
        PrepareScreen(onNavigateBack = onNavigateBack)
    }
}
