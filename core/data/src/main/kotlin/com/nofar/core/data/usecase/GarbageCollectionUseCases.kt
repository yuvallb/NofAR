package com.nofar.core.data.usecase

import com.nofar.core.data.repository.DemTileRepository
import com.nofar.core.database.dao.GeoEntityDao
import com.nofar.core.database.dao.RegionDao
import com.nofar.core.database.dao.RegionEntityCoverageDao
import com.nofar.core.database.dao.TileCoverageDao
import java.util.UUID
import javax.inject.Inject

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
    suspend fun execute(): Int {
        val unused = demTileRepository.getUnusedTiles()
        unused.forEach { demTileRepository.evictTile(it.tileId) }
        return unused.size
    }
}

class LruEvictionUseCase
@Inject
constructor(private val demTileRepository: DemTileRepository) {
    suspend fun execute(thresholdBytes: Long): Int {
        var evicted = 0
        var totalBytes = demTileRepository.totalCacheSizeBytes()
        val candidates = demTileRepository.getLruUnusedCandidates()
        for (tile in candidates) {
            if (totalBytes <= thresholdBytes) break
            demTileRepository.evictTile(tile.tileId)
            totalBytes -= tile.sizeBytes
            evicted++
        }
        return evicted
    }
}
