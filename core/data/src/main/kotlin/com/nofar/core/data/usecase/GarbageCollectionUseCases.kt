package com.nofar.core.data.usecase

import com.nofar.core.data.repository.CoverageSetRepository
import com.nofar.core.data.repository.DemTileRepository
import com.nofar.core.database.dao.CoverageCellDao
import com.nofar.core.database.dao.CoverageEntityDao
import com.nofar.core.database.dao.GeoEntityDao
import com.nofar.core.model.DownloadStatus
import java.util.UUID
import javax.inject.Inject

data class TileEvictionResult(val tilesEvicted: Int, val bytesFreed: Long)

/**
 * Deletes a coverage set and garbage-collects entities and DEM tiles per Requirements §5.3.
 */
class CoverageSetDeletionUseCase
@Inject
constructor(
    private val coverageSetRepository: CoverageSetRepository,
    private val coverageEntityDao: CoverageEntityDao,
    private val geoEntityDao: GeoEntityDao,
    private val coverageCellDao: CoverageCellDao,
    private val demTileRepository: DemTileRepository
) {
    suspend fun execute(coverageSetId: UUID) {
        val coverageSetIdString = coverageSetId.toString()
        val cellIds = coverageCellDao.getCellIdsForCoverageSet(coverageSetIdString)

        geoEntityDao.deleteEntitiesExclusiveToCoverageSet(coverageSetIdString)
        coverageEntityDao.deleteForCoverageSet(coverageSetIdString)
        coverageCellDao.deleteForCoverageSet(coverageSetIdString)

        cellIds.forEach { tileId ->
            demTileRepository.decrementRefCount(tileId)
            if (demTileRepository.getTile(tileId)?.refCount == 0) {
                demTileRepository.evictTile(tileId)
            }
        }

        coverageSetRepository.deleteCoverageSet(coverageSetId)
    }
}

/** @deprecated Use [CoverageSetDeletionUseCase]. */
typealias RegionDeletionUseCase = CoverageSetDeletionUseCase

class EvictUnusedDemTilesUseCase
@Inject
constructor(private val demTileRepository: DemTileRepository) {
    suspend fun execute(): TileEvictionResult {
        val unused = demTileRepository.getUnusedTiles()
        var bytesFreed = 0L
        unused.forEach { tile ->
            bytesFreed += tile.sizeBytes
            demTileRepository.evictTile(tile.tileId)
        }
        return TileEvictionResult(tilesEvicted = unused.size, bytesFreed = bytesFreed)
    }
}

class LruEvictionUseCase
@Inject
constructor(private val demTileRepository: DemTileRepository) {
    suspend fun execute(thresholdBytes: Long): TileEvictionResult {
        var evicted = 0
        var bytesFreed = 0L
        var totalBytes = demTileRepository.totalCacheSizeBytes()
        val candidates = demTileRepository.getLruUnusedCandidates()
        for (tile in candidates) {
            if (totalBytes <= thresholdBytes) break
            demTileRepository.evictTile(tile.tileId)
            totalBytes -= tile.sizeBytes
            bytesFreed += tile.sizeBytes
            evicted++
        }
        return TileEvictionResult(tilesEvicted = evicted, bytesFreed = bytesFreed)
    }
}

/**
 * Evicts least-recently-used tiles regardless of reference count after user confirmation.
 * Affected coverage sets are marked [DownloadStatus.PARTIAL]. Cell geometry is retained so
 * the set can be repaired / re-downloaded.
 */
class ForceLruEvictionUseCase
@Inject
constructor(
    private val demTileRepository: DemTileRepository,
    private val coverageCellDao: CoverageCellDao,
    private val coverageSetRepository: CoverageSetRepository
) {
    suspend fun execute(thresholdBytes: Long): TileEvictionResult {
        var evicted = 0
        var bytesFreed = 0L
        var totalBytes = demTileRepository.totalCacheSizeBytes()
        val candidates = demTileRepository.getAllLruCandidates()
        for (tile in candidates) {
            if (totalBytes <= thresholdBytes) break
            val affectedCoverageSetIds = coverageCellDao.getCoverageSetIdsForCell(tile.tileId)
            // Keep coverage_cell rows — they are the set geometry in v2.
            demTileRepository.evictTile(tile.tileId)
            affectedCoverageSetIds.forEach { coverageSetId ->
                coverageSetRepository.updateDownloadStatus(
                    id = UUID.fromString(coverageSetId),
                    status = DownloadStatus.PARTIAL,
                    progressPct = 100
                )
            }
            totalBytes -= tile.sizeBytes
            bytesFreed += tile.sizeBytes
            evicted++
        }
        return TileEvictionResult(tilesEvicted = evicted, bytesFreed = bytesFreed)
    }
}
