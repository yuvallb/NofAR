package com.nofar.feature.prepare

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val PREPARE_ROUTE = "prepare"

fun NavGraphBuilder.prepareScreen() {
    composable(route = PREPARE_ROUTE) {
        PrepareScreen()
    }
}
