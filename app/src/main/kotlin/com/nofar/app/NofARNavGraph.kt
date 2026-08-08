package com.nofar.app

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.nofar.feature.explore.EXPLORE_ROUTE_WITH_ARGS
import com.nofar.feature.explore.EXPLORE_START_ROUTE
import com.nofar.feature.explore.ExploreRouteBuilder
import com.nofar.feature.explore.exploreScreen
import com.nofar.feature.home.HOME_ROUTE
import com.nofar.feature.home.homeScreen
import com.nofar.feature.prepare.PREPARE_ROUTE_WITH_ARG
import com.nofar.feature.prepare.VIRTUAL_LOCATION_PICKER_ROUTE
import com.nofar.feature.prepare.prepareScreen
import com.nofar.feature.prepare.virtualLocationPickerScreen
import com.nofar.feature.settings.SETTINGS_ROUTE
import com.nofar.feature.settings.settingsScreen
import java.util.UUID

internal fun NavGraphBuilder.nofarNavGraph(navController: NavHostController) {
    homeScreen(
        onNavigateToSettings = { navController.navigate(SETTINGS_ROUTE) },
        onNavigateToPrepare = { regionId -> navController.navigate(buildPrepareRoute(regionId)) },
        onNavigateToExplore = { regionId ->
            navController.navigate(ExploreRouteBuilder.build(regionId = regionId))
        },
        onNavigateToVirtualLocationPicker = {
            navController.navigate(VIRTUAL_LOCATION_PICKER_ROUTE)
        }
    )
    virtualLocationPickerScreen(
        onNavigateBack = { navController.popBackStack() },
        onContinueToVirtualExplore = { selection ->
            navController.navigate(
                ExploreRouteBuilder.build(
                    regionId = selection.primaryRegionId,
                    virtualLat = selection.lat,
                    virtualLon = selection.lon
                )
            ) {
                popUpTo(VIRTUAL_LOCATION_PICKER_ROUTE) { inclusive = true }
                launchSingleTop = true
            }
        }
    )
    prepareScreen(onNavigateBack = { navController.popBackStack() })
    exploreScreen(
        onNavigateBack = { navController.popBackStack() },
        onNavigateToSettings = { navController.navigate(SETTINGS_ROUTE) },
        onChangeVirtualLocation = {
            navController.navigate(VIRTUAL_LOCATION_PICKER_ROUTE) {
                // Destroy the current Explore VM so the singleton VisibilityPassScheduler is not
                // left owned by a back-stacked destination under the next Explore session.
                popUpTo(EXPLORE_ROUTE_WITH_ARGS) { inclusive = true }
                launchSingleTop = true
            }
        }
    )
    settingsScreen(
        onNavigateBack = { navController.popBackStack() },
        onSimpleModeChanged = { enabled ->
            val newRoot = if (enabled) EXPLORE_START_ROUTE else HOME_ROUTE
            navController.navigate(newRoot) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    )
}

internal fun buildPrepareRoute(regionId: UUID?): String = if (regionId == null) {
    PREPARE_ROUTE_WITH_ARG.replace("{regionId}", "")
} else {
    PREPARE_ROUTE_WITH_ARG.replace("{regionId}", regionId.toString())
}
