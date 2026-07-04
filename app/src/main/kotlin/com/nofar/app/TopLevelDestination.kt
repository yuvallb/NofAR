package com.nofar.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import com.nofar.feature.explore.EXPLORE_ROUTE
import com.nofar.feature.home.HOME_ROUTE
import com.nofar.feature.prepare.PREPARE_ROUTE

internal data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit,
)

internal val topLevelDestinations = listOf(
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
