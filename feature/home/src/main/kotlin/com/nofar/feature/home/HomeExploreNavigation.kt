package com.nofar.feature.home

import com.nofar.core.model.CoverageSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class HomeExploreNavigation(private val uiState: MutableStateFlow<HomeUiState>) {
    fun onGlobalEnterExplore(insideExploreCoverageSets: List<CoverageSet>) {
        when (val decision = HomeCoverageLogic.resolveExploreNavigation(insideExploreCoverageSets)) {
            ExploreNavigationDecision.Disabled -> Unit
            is ExploreNavigationDecision.Direct ->
                uiState.update { it.copy(navigateToExploreCoverageSetId = decision.coverageSetId) }
        }
    }

    fun onExploreNavigationHandled() {
        uiState.update { it.copy(navigateToExploreCoverageSetId = null) }
    }
}
