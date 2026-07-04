package com.nofar.feature.explore

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val EXPLORE_ROUTE = "explore"

fun NavGraphBuilder.exploreScreen() {
    composable(route = EXPLORE_ROUTE) {
        ExploreScreen()
    }
}
