package com.nofar.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nofar.feature.explore.EXPLORE_ROUTE
import com.nofar.feature.home.HOME_ROUTE
import com.nofar.feature.prepare.PREPARE_ROUTE

data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(
        route = HOME_ROUTE,
        label = "Home",
        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
    ),
    TopLevelDestination(
        route = PREPARE_ROUTE,
        label = "Prepare",
        icon = { Icon(Icons.Default.Download, contentDescription = "Prepare") },
    ),
    TopLevelDestination(
        route = EXPLORE_ROUTE,
        label = "Explore",
        icon = { Icon(Icons.Default.Explore, contentDescription = "Explore") },
    ),
)

@Composable
fun NofARApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = currentDestination?.route in topLevelDestinations.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = destination.icon,
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NofARNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
