package com.nofar.core.data.usecase

import com.nofar.core.data.repository.DemTileRepository
import com.nofar.core.data.repository.RegionRepository
import com.nofar.core.database.dao.GeoEntityDao
import com.nofar.core.database.dao.RegionDao
import com.nofar.core.database.dao.RegionEntityCoverageDao
import com.nofar.core.database.dao.TileCoverageDao
import com.nofar.core.model.DownloadStatus
import java.util.UUID
import javax.inject.Inject

data class TileEvictionResult(val tilesEvicted: Int, val bytesFreed: Long)

/**
 * Deletes a region and garbage-collects entities and DEM tiles per Requirements §5.3.
 */
class RegionDeletionUseCase
@Inject
constructor(
    private val regionDao: RegionDao,
    private val regionEntityCoverageDao: RegionEntityCoverageDao,
    private val geoEntityDao: GeoEntityDao,
    private val tileCoverageDao: TileCoverageDao,
    private val demTileRepository: DemTileRepository
) {
    suspend fun execute(regionId: UUID) {
        val regionIdString = regionId.toString()
        val tileIds = tileCoverageDao.getTileIdsForRegion(regionIdString)

        geoEntityDao.deleteEntitiesExclusiveToRegion(regionIdString)
        regionEntityCoverageDao.deleteForRegion(regionIdString)
        tileCoverageDao.deleteForRegion(regionIdString)

        tileIds.forEach { tileId ->
            demTileRepository.decrementRefCount(tileId)
            if (demTileRepository.getTile(tileId)?.refCount == 0) {
                demTileRepository.evictTile(tileId)
            }
        }

        regionDao.deleteById(regionIdString)
    }
}

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
 * Affected regions are marked [DownloadStatus.PARTIAL].
 */
class ForceLruEvictionUseCase
@Inject
constructor(
    private val demTileRepository: DemTileRepository,
    private val tileCoverageDao: TileCoverageDao,
    private val regionRepository: RegionRepository
) {
    suspend fun execute(thresholdBytes: Long): TileEvictionResult {
        var evicted = 0
        var bytesFreed = 0L
        var totalBytes = demTileRepository.totalCacheSizeBytes()
        val candidates = demTileRepository.getAllLruCandidates()
        for (tile in candidates) {
            if (totalBytes <= thresholdBytes) break
            val affectedRegionIds = tileCoverageDao.getRegionIdsForTile(tile.tileId)
            tileCoverageDao.deleteForTile(tile.tileId)
            demTileRepository.evictTile(tile.tileId)
            affectedRegionIds.forEach { regionId ->
                regionRepository.updateDownloadStatus(
                    id = UUID.fromString(regionId),
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
