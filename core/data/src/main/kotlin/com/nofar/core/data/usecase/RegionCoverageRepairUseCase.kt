package com.nofar.core.data.usecase

import com.nofar.core.data.dem.RegionDemTileResolver
import com.nofar.core.data.repository.DemTileRepository
import com.nofar.core.database.GeoEntitySpatialQuery
import com.nofar.core.database.RTreeMaintenance
import com.nofar.core.database.dao.CoverageLinker
import com.nofar.core.database.dao.DemTileDao
import com.nofar.core.database.dao.RegionEntityCoverageDao
import com.nofar.core.database.dao.TileCoverageDao
import com.nofar.core.model.DemTileId
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.Region
import com.nofar.core.model.RegionBounds
import com.nofar.core.model.ResolutionLevel
import javax.inject.Inject

/**
 * Repairs missing junction-table rows left by earlier downloads where entities or DEM tiles
 * were persisted but coverage links were not written.
 */
class RegionCoverageRepairUseCase
@Inject
constructor(
    private val rTreeMaintenance: RTreeMaintenance,
    private val regionEntityCoverageDao: RegionEntityCoverageDao,
    private val tileCoverageDao: TileCoverageDao,
    private val demTileDao: DemTileDao,
    private val demTileRepository: DemTileRepository,
    private val coverageLinker: CoverageLinker,
    private val spatialQuery: GeoEntitySpatialQuery
) {
    suspend fun repairIfNeeded(region: Region) {
        rTreeMaintenance.backfillMissingEntriesIfNeeded()
        if (region.downloadStatus != DownloadStatus.READY && region.downloadStatus != DownloadStatus.PARTIAL) {
            return
        }
        repairEntityCoverage(region)
        repairTileCoverage(region)
    }

    private suspend fun repairEntityCoverage(region: Region) {
        val regionId = region.id.toString()
        if (regionEntityCoverageDao.getEntityIdsForRegion(regionId).isNotEmpty()) return
        val entities =
            spatialQuery.queryWithinRadius(
                lat = region.centerLat,
                lon = region.centerLon,
                radiusM = region.radiusM,
                resolutionLevel = ResolutionLevel.Full
            )
        if (entities.isNotEmpty()) {
            coverageLinker.linkEntities(regionId, entities.map { it.id to it.name })
        }
    }

    private suspend fun repairTileCoverage(region: Region) {
        val regionId = region.id.toString()
        registerIntersectingBins(region)
        val tileIds =
            RegionDemTileResolver.resolveTileIds(
                region = region,
                tileCoverageDao = tileCoverageDao,
                demTileDao = demTileDao,
                tileReadable = demTileRepository::isBinReadable
            )
        if (tileIds.isEmpty()) return
        if (tileCoverageDao.getTileIdsForRegion(regionId).isEmpty()) {
            coverageLinker.linkTiles(regionId, tileIds)
        }
        tileIds.forEach { tileId ->
            if (demTileRepository.getTile(tileId)?.refCount == 0) {
                demTileRepository.incrementRefCount(tileId)
            }
        }
    }

    private suspend fun registerIntersectingBins(region: Region) {
        DemTileId.intersectingTiles(
            RegionBounds.boundingBox(region.centerLat, region.centerLon, region.radiusM)
        ).map { (tileLat, tileLon) -> DemTileId.fromCoordinates(tileLat, tileLon) }
            .forEach { tileId -> demTileRepository.ensureRegisteredFromBin(tileId) }
    }
}
