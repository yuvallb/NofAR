package com.nofar.feature.home

import androidx.lifecycle.SavedStateHandle
import com.nofar.core.model.Region
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class HomeExploreNavigation(private val uiState: MutableStateFlow<HomeUiState>) {
    fun onGlobalEnterExplore(insideExploreRegions: List<Region>) {
        applyDecision(HomeRegionLogic.resolveExploreNavigation(insideExploreRegions))
    }

    fun onOverlappingRegionSelected(regionId: UUID, savedStateHandle: SavedStateHandle) {
        savedStateHandle[HomeViewModel.LAST_SELECTED_REGION_KEY] = regionId.toString()
        uiState.update {
            it.copy(
                overlappingRegionsDialog = null,
                lastSelectedOverlapRegionId = regionId,
                navigateToExploreRegionId = regionId
            )
        }
    }

    fun dismissOverlappingRegionsDialog() {
        uiState.update { it.copy(overlappingRegionsDialog = null) }
    }

    fun onExploreNavigationHandled() {
        uiState.update { it.copy(navigateToExploreRegionId = null) }
    }

    private fun applyDecision(decision: ExploreNavigationDecision) {
        when (decision) {
            ExploreNavigationDecision.Disabled -> Unit
            is ExploreNavigationDecision.Direct ->
                uiState.update { it.copy(navigateToExploreRegionId = decision.regionId) }
            is ExploreNavigationDecision.OverlapPicker ->
                uiState.update { it.copy(overlappingRegionsDialog = decision.regions) }
        }
    }
}
