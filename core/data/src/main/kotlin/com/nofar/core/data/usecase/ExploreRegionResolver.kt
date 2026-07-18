package com.nofar.core.data.usecase

import com.nofar.core.data.prepare.PrepareEstimator
import com.nofar.core.data.prepare.RegionNamePolicy
import com.nofar.core.model.AppConfig
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.Region
import com.nofar.core.model.RegionBounds

sealed interface ExploreRegionResolution {
    data class Active(val region: Region) : ExploreRegionResolution

    data class Downloading(val region: Region) : ExploreRegionResolution

    data class NeedsDownload(val proposal: QuickRegionProposal) : ExploreRegionResolution
}

object ExploreRegionResolver {
    private val exploreEligibleStatuses = setOf(DownloadStatus.READY, DownloadStatus.PARTIAL)

    fun resolve(
        regionsAtPoint: List<Region>,
        downloadingRegion: Region?,
        lat: Double,
        lon: Double
    ): ExploreRegionResolution {
        val active =
            regionsAtPoint
                .filter { it.downloadStatus in exploreEligibleStatuses }
                .maxByOrNull { it.updatedAt }
        val downloadingAtPoint =
            regionsAtPoint
                .filter { it.downloadStatus == DownloadStatus.DOWNLOADING }
                .maxByOrNull { it.updatedAt }
        val notDownloadedAtPoint =
            regionsAtPoint
                .filter { it.downloadStatus == DownloadStatus.NOT_DOWNLOADED }
                .maxByOrNull { it.updatedAt }

        return when {
            active != null -> ExploreRegionResolution.Active(active)
            downloadingAtPoint != null -> ExploreRegionResolution.Downloading(downloadingAtPoint)
            notDownloadedAtPoint != null ->
                ExploreRegionResolution.NeedsDownload(proposalForRegion(notDownloadedAtPoint))
            downloadingRegion != null -> ExploreRegionResolution.Downloading(downloadingRegion)
            else -> ExploreRegionResolution.NeedsDownload(proposalAtLocation(lat, lon))
        }
    }

    private fun proposalAtLocation(lat: Double, lon: Double): QuickRegionProposal {
        val radiusM = AppConfig.SIMPLE_MODE_DEFAULT_RADIUS_M
        val estimate =
            PrepareEstimator.estimate(lat, lon, RegionBounds.dataCollectionRadiusM(radiusM))
        return QuickRegionProposal(
            centerLat = lat,
            centerLon = lon,
            radiusM = radiusM,
            name = RegionNamePolicy.formatAutoName(lat, lon),
            estimateBytes = estimate.totalEstimateBytes,
            demTileCount = estimate.demTileCount
        )
    }

    private fun proposalForRegion(region: Region): QuickRegionProposal = QuickRegionProposal(
        centerLat = region.centerLat,
        centerLon = region.centerLon,
        radiusM = region.radiusM,
        name = region.name,
        estimateBytes = region.estimatedSizeBytes,
        demTileCount =
        PrepareEstimator.estimate(
            region.centerLat,
            region.centerLon,
            RegionBounds.dataCollectionRadiusM(region)
        ).demTileCount,
        existingRegionId = region.id
    )
}
