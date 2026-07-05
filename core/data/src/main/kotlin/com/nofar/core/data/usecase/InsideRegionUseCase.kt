package com.nofar.core.data.usecase

import com.nofar.core.data.repository.RegionRepository
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.Region
import com.nofar.core.model.RegionBounds
import java.util.UUID
import javax.inject.Inject

/**
 * Determines whether a GPS point lies inside a circular region and which stored regions contain it.
 * Uses haversine distance from center to point (Requirements §3.1).
 */
class InsideRegionUseCase
@Inject
constructor(private val regionRepository: RegionRepository) {
    fun isInsideRegion(region: Region, lat: Double, lon: Double): Boolean = RegionBounds.containsPoint(region, lat, lon)

    fun isExploreEligible(region: Region): Boolean =
        region.downloadStatus == DownloadStatus.READY || region.downloadStatus == DownloadStatus.PARTIAL

    suspend fun regionsContainingPoint(lat: Double, lon: Double): List<Region> =
        regionRepository.regionsContainingPoint(lat, lon)

    suspend fun exploreEligibleRegionsContainingPoint(lat: Double, lon: Double): List<Region> =
        regionsContainingPoint(lat, lon).filter(::isExploreEligible)

    suspend fun insideRegionIds(lat: Double, lon: Double, regions: List<Region>): Set<UUID> =
        regions.filter { isInsideRegion(it, lat, lon) }.map { it.id }.toSet()
}
