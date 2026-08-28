package com.nofar.feature.home

import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.Region
import com.nofar.core.model.RegionBounds
import com.nofar.core.model.UserLocation
import java.util.UUID

private val EXPLORE_ELIGIBLE_STATUSES = setOf(DownloadStatus.READY, DownloadStatus.PARTIAL)

private val EXPLORE_REGION_PREFERENCE: Comparator<Region> =
    compareBy<Region> { it.downloadStatus == DownloadStatus.READY }
        .thenBy { it.updatedAt }

sealed interface ExploreNavigationDecision {
    data object Disabled : ExploreNavigationDecision

    data class Direct(val regionId: UUID) : ExploreNavigationDecision
}

internal object HomeRegionLogic {
    fun sortRegionsForDisplay(regions: List<Region>, location: UserLocation?): List<Region> {
        if (location == null) return regions.sortedByDescending { it.updatedAt }
        return regions.sortedWith(
            compareBy<Region> { region ->
                !RegionBounds.containsPoint(region, location.latitude, location.longitude)
            }.thenBy { region ->
                RegionBounds.haversineDistanceM(
                    region.centerLat,
                    region.centerLon,
                    location.latitude,
                    location.longitude
                )
            }.thenByDescending { it.updatedAt }
        )
    }

    fun shouldShowYouAreHere(region: Region, isInside: Boolean): Boolean =
        isInside && region.downloadStatus in EXPLORE_ELIGIBLE_STATUSES

    fun exploreEligibleInside(regions: List<Region>, location: UserLocation?): List<Region> {
        if (location == null) return emptyList()
        return regions.filter { region ->
            RegionBounds.containsPoint(region, location.latitude, location.longitude) &&
                region.downloadStatus in EXPLORE_ELIGIBLE_STATUSES
        }
    }

    fun isEnterExploreEnabled(insideExploreRegions: List<Region>): Boolean = insideExploreRegions.isNotEmpty()

    fun resolveExploreNavigation(insideExploreRegions: List<Region>): ExploreNavigationDecision {
        val selected =
            insideExploreRegions.maxWithOrNull(EXPLORE_REGION_PREFERENCE)
                ?: return ExploreNavigationDecision.Disabled
        return ExploreNavigationDecision.Direct(selected.id)
    }
}
