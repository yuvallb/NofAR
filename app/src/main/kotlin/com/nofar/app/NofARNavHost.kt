package com.nofar.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.nofar.feature.explore.EXPLORE_START_ROUTE
import com.nofar.feature.home.HOME_ROUTE

@Composable
fun NofARNavHost(navController: NavHostController, simpleModeEnabled: Boolean, modifier: Modifier = Modifier) {
    val startDestination = if (simpleModeEnabled) EXPLORE_START_ROUTE else HOME_ROUTE
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        nofarNavGraph(navController)
    }
}
