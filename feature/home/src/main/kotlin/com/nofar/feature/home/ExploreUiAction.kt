package com.nofar.feature.home

sealed interface ExploreUiAction {
    data object NavigationHandled : ExploreUiAction
}
