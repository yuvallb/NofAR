package com.nofar.feature.home

import com.nofar.core.data.repository.RegionRepository
import com.nofar.core.designsystem.component.RegionCardState
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.Region
import com.nofar.core.model.UserLocation

internal suspend fun buildHomeRegionCards(
    regionRepository: RegionRepository,
    regions: List<Region>,
    location: UserLocation?
): List<RegionCardState> {
    val sorted = regions.sortedByDescending { it.updatedAt }
    val containingIds =
        if (location != null) {
            regionRepository
                .regionsContainingPoint(location.latitude, location.longitude)
                .map { it.id }
                .toSet()
        } else {
            emptySet()
        }
    return sorted.map { region ->
        val isHere = region.id in containingIds
        val canExplore =
            isHere &&
                (region.downloadStatus == DownloadStatus.READY || region.downloadStatus == DownloadStatus.PARTIAL)
        RegionCardState(
            region = region,
            isYouAreHere = isHere,
            canEnterExplore = canExplore
        )
    }
}
