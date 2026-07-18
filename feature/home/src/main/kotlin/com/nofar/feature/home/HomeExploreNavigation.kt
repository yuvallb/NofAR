package com.nofar.feature.home

import com.nofar.core.model.Region
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class HomeExploreNavigation(private val uiState: MutableStateFlow<HomeUiState>) {
    fun onGlobalEnterExplore(insideExploreRegions: List<Region>) {
        applyDecision(HomeRegionLogic.resolveExploreNavigation(insideExploreRegions))
    }

    fun onExploreNavigationHandled() {
        uiState.update { it.copy(navigateToExploreRegionId = null) }
    }

    private fun applyDecision(decision: ExploreNavigationDecision) {
        when (decision) {
            ExploreNavigationDecision.Disabled -> Unit
            is ExploreNavigationDecision.Direct ->
                uiState.update { it.copy(navigateToExploreRegionId = decision.regionId) }
        }
    }
}
