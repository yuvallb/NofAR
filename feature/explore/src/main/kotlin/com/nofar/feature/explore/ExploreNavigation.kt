package com.nofar.feature.explore

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

const val EXPLORE_ROUTE = "explore"
const val EXPLORE_ROUTE_WITH_ARG = "explore?regionId={regionId}"

fun NavGraphBuilder.exploreScreen(onNavigateBack: () -> Unit) {
    composable(
        route = EXPLORE_ROUTE_WITH_ARG,
        arguments =
        listOf(
            navArgument("regionId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
    ) {
        ExploreScreen(onNavigateBack = onNavigateBack)
    }
}
