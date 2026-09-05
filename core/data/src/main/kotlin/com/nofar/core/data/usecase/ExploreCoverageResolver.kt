package com.nofar.core.data.usecase

import com.nofar.core.data.prepare.PrepareEstimator
import com.nofar.core.data.prepare.RegionNamePolicy
import com.nofar.core.model.AppConfig
import com.nofar.core.model.CellMembership
import com.nofar.core.model.CountryPackCatalog
import com.nofar.core.model.CoverageSet
import com.nofar.core.model.DemTileId
import com.nofar.core.model.DownloadStatus

sealed interface ExploreCoverageResolution {
    data class Active(val coverageSet: CoverageSet) : ExploreCoverageResolution

    data class Downloading(val coverageSet: CoverageSet) : ExploreCoverageResolution

    data class NeedsDownload(val proposal: QuickCoverageProposal) : ExploreCoverageResolution
}

object ExploreCoverageResolver {
    private val exploreEligibleStatuses = setOf(DownloadStatus.READY, DownloadStatus.PARTIAL)

    fun resolve(
        coverageSetsAtPoint: List<CoverageSet>,
        downloadingCoverageSet: CoverageSet?,
        lat: Double,
        lon: Double,
        cacheLimitBytes: Long
    ): ExploreCoverageResolution {
        val active =
            coverageSetsAtPoint
                .filter { it.downloadStatus in exploreEligibleStatuses }
                .maxByOrNull { it.updatedAt }
        val downloadingAtPoint =
            coverageSetsAtPoint
                .filter { it.downloadStatus == DownloadStatus.DOWNLOADING }
                .maxByOrNull { it.updatedAt }
        val notDownloadedAtPoint =
            coverageSetsAtPoint
                .filter { it.downloadStatus == DownloadStatus.NOT_DOWNLOADED }
                .maxByOrNull { it.updatedAt }

        return when {
            active != null -> ExploreCoverageResolution.Active(active)
            downloadingAtPoint != null -> ExploreCoverageResolution.Downloading(downloadingAtPoint)
            notDownloadedAtPoint != null ->
                ExploreCoverageResolution.NeedsDownload(proposalForCoverageSet(notDownloadedAtPoint, lat, lon))
            downloadingCoverageSet != null ->
                ExploreCoverageResolution.Downloading(downloadingCoverageSet)
            else -> ExploreCoverageResolution.NeedsDownload(proposalAtLocation(lat, lon, cacheLimitBytes))
        }
    }

    private fun proposalAtLocation(lat: Double, lon: Double, cacheLimitBytes: Long): QuickCoverageProposal {
        val pack = CountryPackCatalog.packsOfferedAt(lat, lon, cacheLimitBytes).firstOrNull()
        if (pack != null) {
            val cells = pack.cellIds.mapNotNull(DemTileId::parse)
            val estimate = PrepareEstimator.estimateForCells(cells)
            val requiredCacheBytes = estimate.demEstimateMinBytes + AppConfig.PACK_CACHE_HEADROOM_BYTES
            return QuickCoverageProposal(
                centerLat = lat,
                centerLon = lon,
                cellIds = pack.cellIds,
                name = pack.displayName,
                estimateBytes = estimate.totalEstimateBytes,
                demTileCount = estimate.demTileCount,
                packCacheRaiseBytes = requiredCacheBytes.takeIf { it > cacheLimitBytes }
            )
        }
        val cellIds = CellMembership.localDownloadCellIds(lat, lon)
        val cells = cellIds.mapNotNull { DemTileId.parse(it) }
        val estimate = PrepareEstimator.estimateForCells(cells)
        return QuickCoverageProposal(
            centerLat = lat,
            centerLon = lon,
            cellIds = cellIds,
            name = RegionNamePolicy.formatAutoName(lat, lon),
            estimateBytes = estimate.totalEstimateBytes,
            demTileCount = estimate.demTileCount
        )
    }

    private fun proposalForCoverageSet(coverageSet: CoverageSet, lat: Double, lon: Double): QuickCoverageProposal {
        // Prefer re-downloading the existing set's cells when present; fall back to local 3×3.
        val cellIds = CellMembership.localDownloadCellIds(lat, lon)
        val cells = cellIds.mapNotNull { DemTileId.parse(it) }
        return QuickCoverageProposal(
            centerLat = lat,
            centerLon = lon,
            cellIds = cellIds,
            name = coverageSet.name,
            estimateBytes = coverageSet.estimatedSizeBytes.takeIf { it > 0 }
                ?: PrepareEstimator.estimateForCells(cells).totalEstimateBytes,
            demTileCount = PrepareEstimator.estimateForCells(cells).demTileCount,
            existingCoverageSetId = coverageSet.id
        )
    }
}
