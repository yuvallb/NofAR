package com.nofar.feature.home

import com.nofar.core.data.repository.HomeRegionMetadataRepository
import com.nofar.core.data.usecase.InsideRegionUseCase
import com.nofar.core.designsystem.component.RegionCardState
import com.nofar.core.model.Region
import com.nofar.core.model.UserLocation
import kotlin.math.max

internal suspend fun buildHomeRegionCards(
    insideRegionUseCase: InsideRegionUseCase,
    metadataRepository: HomeRegionMetadataRepository,
    regions: List<Region>,
    location: UserLocation?
): List<RegionCardState> {
    val sorted = HomeRegionLogic.sortRegionsByUpdatedAt(regions)
    val insideIds =
        if (location != null) {
            insideRegionUseCase.insideRegionIds(location.latitude, location.longitude, regions)
        } else {
            emptySet()
        }
    return sorted.map { region ->
        val isInside = region.id in insideIds
        val metadata = metadataRepository.getMetadata(region.id)
        val demSizeBytes = metadata.demSizeBytes
        val osmSizeBytes =
            if (demSizeBytes > 0L) {
                max(0L, region.estimatedSizeBytes - demSizeBytes)
            } else {
                region.estimatedSizeBytes
            }
        RegionCardState(
            region = region,
            isYouAreHere = HomeRegionLogic.shouldShowYouAreHere(region, isInside),
            canEnterExplore = HomeRegionLogic.canEnterExplore(region, isInside),
            osmSizeBytes = osmSizeBytes,
            demSizeBytes = demSizeBytes,
            latestDemTimestamp = metadata.latestDemTimestamp
        )
    }
}
