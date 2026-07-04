package com.nofar.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.nofar.feature.explore.exploreScreen
import com.nofar.feature.home.homeScreen
import com.nofar.feature.prepare.prepareScreen
import com.nofar.feature.settings.SETTINGS_ROUTE
import com.nofar.feature.settings.settingsScreen
import com.nofar.feature.home.HOME_ROUTE

@Composable
fun NofARNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = HOME_ROUTE,
        modifier = modifier,
    ) {
        homeScreen(
            onNavigateToSettings = { navController.navigate(SETTINGS_ROUTE) },
        )
        prepareScreen()
        exploreScreen()
        settingsScreen(
            onNavigateBack = { navController.popBackStack() },
        )
    }
}
