package com.nofar.app

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.nofar.feature.explore.EXPLORE_ROUTE_WITH_ARGS
import com.nofar.feature.explore.EXPLORE_START_ROUTE
import com.nofar.feature.explore.ExplorePrepareNavigation
import com.nofar.feature.explore.ExploreRouteBuilder
import com.nofar.feature.explore.exploreScreen
import com.nofar.feature.home.HOME_ROUTE
import com.nofar.feature.home.homeScreen
import com.nofar.feature.prepare.PrepareRouteBuilder
import com.nofar.feature.prepare.VIRTUAL_LOCATION_PICKER_ROUTE
import com.nofar.feature.prepare.VirtualLocationSelection
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
            navController.navigateToVirtualExplore(selection)
        }
    )
    prepareScreen(
        onNavigateBack = { navController.popBackStack() },
        onNavigateToSettings = { navController.navigate(SETTINGS_ROUTE) }
    )
    exploreScreen(
        onNavigateBack = { navController.popBackStack() },
        onNavigateToSettings = { navController.navigate(SETTINGS_ROUTE) },
        onNavigateToPrepare = { target -> navController.navigateToPrepareFromExplore(target) },
        onChangeVirtualLocation = { navController.navigateToChangeVirtualLocation() }
    )
    settingsScreen(
        onNavigateBack = { navController.popBackStack() },
        onSimpleModeChanged = { enabled -> navController.replaceRootForSimpleMode(enabled) }
    )
}

internal fun buildPrepareRoute(regionId: UUID?): String = PrepareRouteBuilder.build(regionId = regionId)

private fun NavHostController.navigateToVirtualExplore(selection: VirtualLocationSelection) {
    navigate(
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

private fun NavHostController.navigateToPrepareFromExplore(target: ExplorePrepareNavigation) {
    navigate(
        PrepareRouteBuilder.build(
            regionId = target.regionId,
            centerLat = target.centerLat,
            centerLon = target.centerLon,
            radiusM = target.radiusM,
            name = target.name
        )
    )
}

private fun NavHostController.navigateToChangeVirtualLocation() {
    navigate(VIRTUAL_LOCATION_PICKER_ROUTE) {
        // Destroy the current Explore VM so the singleton VisibilityPassScheduler is not
        // left owned by a back-stacked destination under the next Explore session.
        popUpTo(EXPLORE_ROUTE_WITH_ARGS) { inclusive = true }
        launchSingleTop = true
    }
}

private fun NavHostController.replaceRootForSimpleMode(enabled: Boolean) {
    val newRoot = if (enabled) EXPLORE_START_ROUTE else HOME_ROUTE
    navigate(newRoot) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
    }
}
