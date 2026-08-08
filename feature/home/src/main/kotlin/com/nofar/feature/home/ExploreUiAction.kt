package com.nofar.feature.home

import java.util.UUID

sealed interface ExploreUiAction {
    data object NavigationHandled : ExploreUiAction

    data class OverlapRegionSelected(val regionId: UUID) : ExploreUiAction

    data object OverlapPickerDismissed : ExploreUiAction
}
