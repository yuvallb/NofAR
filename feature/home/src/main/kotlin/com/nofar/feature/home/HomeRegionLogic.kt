package com.nofar.feature.home

import com.nofar.core.model.CellMembership
import com.nofar.core.model.CoverageSet
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.UserLocation
import java.util.UUID

private val EXPLORE_ELIGIBLE_STATUSES = setOf(DownloadStatus.READY, DownloadStatus.PARTIAL)

private val EXPLORE_COVERAGE_PREFERENCE: Comparator<CoverageSet> =
    compareBy<CoverageSet> { it.downloadStatus == DownloadStatus.READY }
        .thenBy { it.updatedAt }

sealed interface ExploreNavigationDecision {
    data object Disabled : ExploreNavigationDecision

    data class Direct(val coverageSetId: UUID) : ExploreNavigationDecision
}

internal object HomeCoverageLogic {
    fun sortCoverageSetsForDisplay(
        coverageSets: List<CoverageSet>,
        location: UserLocation?,
        cellIdsBySet: Map<UUID, Set<String>>
    ): List<CoverageSet> {
        if (location == null) return coverageSets.sortedByDescending { it.updatedAt }
        return coverageSets.sortedWith(
            compareBy<CoverageSet> { set ->
                val cells = cellIdsBySet[set.id].orEmpty()
                !CellMembership.hasCell(cells, location.latitude, location.longitude)
            }.thenByDescending { it.updatedAt }
        )
    }

    fun shouldShowYouAreHere(coverageSet: CoverageSet, isInside: Boolean): Boolean =
        isInside && coverageSet.downloadStatus in EXPLORE_ELIGIBLE_STATUSES

    fun exploreEligibleInside(
        coverageSets: List<CoverageSet>,
        location: UserLocation?,
        cellIdsBySet: Map<UUID, Set<String>>
    ): List<CoverageSet> {
        if (location == null) return emptyList()
        return coverageSets.filter { set ->
            val cells = cellIdsBySet[set.id].orEmpty()
            CellMembership.hasCell(cells, location.latitude, location.longitude) &&
                set.downloadStatus in EXPLORE_ELIGIBLE_STATUSES
        }
    }

    fun isEnterExploreEnabled(insideExplore: List<CoverageSet>): Boolean = insideExplore.isNotEmpty()

    fun showsEmptyCoveragePrompt(loading: Boolean, coverageSetCount: Int): Boolean = !loading && coverageSetCount == 0

    fun resolveExploreNavigation(insideExplore: List<CoverageSet>): ExploreNavigationDecision {
        val selected =
            insideExplore.maxWithOrNull(EXPLORE_COVERAGE_PREFERENCE)
                ?: return ExploreNavigationDecision.Disabled
        return ExploreNavigationDecision.Direct(selected.id)
    }
}
