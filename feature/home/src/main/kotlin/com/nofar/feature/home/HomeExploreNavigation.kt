package com.nofar.feature.home

import com.nofar.core.model.Region
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class HomeExploreNavigation(private val uiState: MutableStateFlow<HomeUiState>) {
    fun onGlobalEnterExplore(insideExploreRegions: List<Region>) {
        applyDecision(HomeRegionLogic.resolveExploreNavigation(insideExploreRegions))
    }

    fun onExploreNavigationHandled() {
        uiState.update { it.copy(navigateToExploreRegionId = null) }
    }

    fun onOverlapRegionSelected(regionId: UUID) {
        uiState.update {
            it.copy(
                showOverlapPicker = false,
                overlapRegions = emptyList(),
                navigateToExploreRegionId = regionId
            )
        }
    }

    fun dismissOverlapPicker() {
        uiState.update {
            it.copy(showOverlapPicker = false, overlapRegions = emptyList())
        }
    }

    private fun applyDecision(decision: ExploreNavigationDecision) {
        when (decision) {
            ExploreNavigationDecision.Disabled -> Unit
            is ExploreNavigationDecision.Direct ->
                uiState.update {
                    it.copy(
                        showOverlapPicker = false,
                        overlapRegions = emptyList(),
                        navigateToExploreRegionId = decision.regionId
                    )
                }
            is ExploreNavigationDecision.Pick ->
                uiState.update {
                    it.copy(
                        showOverlapPicker = true,
                        overlapRegions = decision.regions,
                        navigateToExploreRegionId = null
                    )
                }
        }
    }
}
