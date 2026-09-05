package com.nofar.feature.explore

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

fun NavGraphBuilder.exploreScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onChangeVirtualLocation: () -> Unit
) {
    composable(
        route = EXPLORE_ROUTE_WITH_ARGS,
        arguments =
        listOf(
            navArgument("coverageSetId") {
                type = NavType.StringType
                nullable = true
                defaultValue = ""
            },
            navArgument("virtualLat") {
                type = NavType.StringType
                nullable = true
                defaultValue = ""
            },
            navArgument("virtualLon") {
                type = NavType.StringType
                nullable = true
                defaultValue = ""
            }
        )
    ) {
        ExploreScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToSettings = onNavigateToSettings,
            onChangeVirtualLocation = onChangeVirtualLocation
        )
    }
}
