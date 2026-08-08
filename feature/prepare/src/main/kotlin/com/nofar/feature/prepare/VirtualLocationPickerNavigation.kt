package com.nofar.feature.prepare

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val VIRTUAL_LOCATION_PICKER_ROUTE = "virtual_location_picker"

fun NavGraphBuilder.virtualLocationPickerScreen(
    onNavigateBack: () -> Unit,
    onContinueToVirtualExplore: (VirtualLocationSelection) -> Unit
) {
    composable(route = VIRTUAL_LOCATION_PICKER_ROUTE) {
        VirtualLocationPickerScreen(
            onNavigateBack = onNavigateBack,
            onContinueToVirtualExplore = onContinueToVirtualExplore
        )
    }
}
