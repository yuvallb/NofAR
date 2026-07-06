package com.nofar.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.nofar.feature.explore.EXPLORE_ROUTE_WITH_ARG
import com.nofar.feature.explore.exploreScreen
import com.nofar.feature.home.HOME_ROUTE
import com.nofar.feature.home.homeScreen
import com.nofar.feature.prepare.PREPARE_ROUTE_WITH_ARG
import com.nofar.feature.prepare.prepareScreen
import com.nofar.feature.settings.SETTINGS_ROUTE
import com.nofar.feature.settings.settingsScreen

@Composable
fun NofARNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = HOME_ROUTE,
        modifier = modifier
    ) {
        homeScreen(
            onNavigateToSettings = { navController.navigate(SETTINGS_ROUTE) },
            onNavigateToPrepare = { regionId ->
                val route =
                    if (regionId == null) {
                        "$PREPARE_ROUTE_WITH_ARG".replace("{regionId}", "")
                    } else {
                        PREPARE_ROUTE_WITH_ARG.replace("{regionId}", regionId.toString())
                    }
                navController.navigate(route)
            },
            onNavigateToExplore = { regionId ->
                navController.navigate(EXPLORE_ROUTE_WITH_ARG.replace("{regionId}", regionId.toString()))
            }
        )
        prepareScreen(
            onNavigateBack = { navController.popBackStack() }
        )
        exploreScreen(
            onNavigateBack = { navController.popBackStack() }
        )
        settingsScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }
}
