package com.nofar.feature.home

import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.Region
import java.util.UUID

private val EXPLORE_ELIGIBLE_STATUSES = setOf(DownloadStatus.READY, DownloadStatus.PARTIAL)

sealed interface ExploreNavigationDecision {
    data object Disabled : ExploreNavigationDecision

    data class Direct(val regionId: UUID) : ExploreNavigationDecision

    data class OverlapPicker(val regions: List<Region>) : ExploreNavigationDecision
}

internal object HomeRegionLogic {
    fun sortRegionsByUpdatedAt(regions: List<Region>): List<Region> = regions.sortedByDescending { it.updatedAt }

    fun shouldShowYouAreHere(region: Region, isInside: Boolean): Boolean =
        isInside && region.downloadStatus in EXPLORE_ELIGIBLE_STATUSES

    fun canEnterExplore(region: Region, isInside: Boolean): Boolean = shouldShowYouAreHere(region, isInside)

    fun isEnterExploreEnabled(insideExploreRegions: List<Region>): Boolean = insideExploreRegions.isNotEmpty()

    fun resolveExploreNavigation(insideExploreRegions: List<Region>): ExploreNavigationDecision = when {
        insideExploreRegions.isEmpty() -> ExploreNavigationDecision.Disabled
        insideExploreRegions.size == 1 -> ExploreNavigationDecision.Direct(insideExploreRegions.single().id)
        else -> ExploreNavigationDecision.OverlapPicker(insideExploreRegions)
    }

    fun resolveExploreNavigationForRegion(
        insideExploreRegions: List<Region>,
        regionId: UUID
    ): ExploreNavigationDecision {
        val target = insideExploreRegions.find { it.id == regionId } ?: return ExploreNavigationDecision.Disabled
        return when {
            insideExploreRegions.size > 1 -> ExploreNavigationDecision.OverlapPicker(insideExploreRegions)
            else -> ExploreNavigationDecision.Direct(target.id)
        }
    }

    fun defaultOverlapSelection(regions: List<Region>, lastSelectedRegionId: UUID?): UUID =
        regions.firstOrNull { it.id == lastSelectedRegionId }?.id ?: regions.first().id
}
