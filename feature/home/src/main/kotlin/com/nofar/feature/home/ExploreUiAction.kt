package com.nofar.feature.home

sealed interface ExploreUiAction {
    data object DismissOverlap : ExploreUiAction

    data object NavigationHandled : ExploreUiAction
}
