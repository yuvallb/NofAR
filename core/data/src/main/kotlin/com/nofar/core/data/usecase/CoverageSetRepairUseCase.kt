@file:Suppress("ReturnCount")

package com.nofar.core.data.usecase

import com.nofar.core.data.dem.CoverageDemTileResolver
import com.nofar.core.data.prepare.MissingEntityElevationFiller
import com.nofar.core.data.repository.CoverageSetRepository
import com.nofar.core.data.repository.DemTileRepository
import com.nofar.core.database.GeoEntitySpatialQuery
import com.nofar.core.database.RTreeMaintenance
import com.nofar.core.database.dao.CoverageCellDao
import com.nofar.core.database.dao.CoverageEntityDao
import com.nofar.core.database.dao.CoverageLinker
import com.nofar.core.database.dao.DemTileDao
import com.nofar.core.database.dao.GeoEntityDao
import com.nofar.core.model.CellMembership
import com.nofar.core.model.CoverageSet
import com.nofar.core.model.DemTileId
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.ResolutionLevel
import javax.inject.Inject

/**
 * Repairs missing junction-table rows and backfills entity elevations from DEM.
 */
class CoverageSetRepairUseCase
@Inject
constructor(
    private val rTreeMaintenance: RTreeMaintenance,
    private val coverageSetRepository: CoverageSetRepository,
    private val coverageEntityDao: CoverageEntityDao,
    private val coverageCellDao: CoverageCellDao,
    private val geoEntityDao: GeoEntityDao,
    private val demTileDao: DemTileDao,
    private val demTileRepository: DemTileRepository,
    private val coverageLinker: CoverageLinker,
    private val spatialQuery: GeoEntitySpatialQuery,
    private val elevationFiller: MissingEntityElevationFiller
) {
    suspend fun repairIfNeeded(coverageSet: CoverageSet) {
        rTreeMaintenance.backfillMissingEntriesIfNeeded()
        if (coverageSet.downloadStatus != DownloadStatus.READY &&
            coverageSet.downloadStatus != DownloadStatus.PARTIAL
        ) {
            return
        }
        repairEntityCoverage(coverageSet)
        repairCellCoverage(coverageSet)
        fillMissingElevations(coverageSet)
    }

    private suspend fun repairEntityCoverage(coverageSet: CoverageSet) {
        val coverageSetId = coverageSet.id.toString()
        if (coverageEntityDao.getEntityIdsForCoverageSet(coverageSetId).isNotEmpty()) return
        val cellIds = coverageCellDao.getCellIdsForCoverageSet(coverageSetId)
        if (cellIds.isEmpty()) return
        val cells = cellIds.mapNotNull { DemTileId.parse(it) }
        if (cells.isEmpty()) return
        val targetCellIds = cellIds.toSet()
        val entities =
            cells.flatMap { (tileLat, tileLon) ->
                spatialQuery.queryWithinRadius(
                    lat = tileLat + 0.5,
                    lon = tileLon + 0.5,
                    radiusM = CELL_HALF_DIAGONAL_RADIUS_M,
                    resolutionLevel = ResolutionLevel.Full
                )
            }.filter { entity ->
                CellMembership.cellIdForPoint(entity.lat, entity.lon) in targetCellIds
            }.distinctBy { it.id }
        if (entities.isNotEmpty()) {
            coverageLinker.linkEntities(coverageSetId, entities.map { it.id to it.name })
        }
    }

    private suspend fun repairCellCoverage(coverageSet: CoverageSet) {
        val coverageSetId = coverageSet.id.toString()
        val cellIds = coverageCellDao.getCellIdsForCoverageSet(coverageSetId)
        registerCellsFromBins(cellIds)
        val tileIds =
            CoverageDemTileResolver.resolveTileIds(
                cellIds = cellIds,
                demTileDao = demTileDao,
                tileReadable = demTileRepository::isBinReadable
            )
        if (tileIds.isEmpty()) return
        tileIds.forEach { tileId ->
            if (demTileRepository.getTile(tileId)?.refCount == 0) {
                demTileRepository.incrementRefCount(tileId)
            }
        }
    }

    private suspend fun fillMissingElevations(coverageSet: CoverageSet) {
        val entityIds = geoEntityDao.getIdsMissingElevationForCoverageSet(coverageSet.id.toString())
        if (entityIds.isEmpty()) return
        elevationFiller.fill(entityIds, refreshDemSamples = false)
    }

    private suspend fun registerCellsFromBins(cellIds: List<String>) {
        cellIds.forEach { cellId ->
            demTileRepository.ensureRegisteredFromBin(cellId)
        }
    }

    companion object {
        private const val CELL_HALF_DIAGONAL_RADIUS_M = 80_000.0
    }
}

/** @deprecated Use [CoverageSetRepairUseCase]. */
typealias RegionCoverageRepairUseCase = CoverageSetRepairUseCase
