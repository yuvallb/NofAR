package com.nofar.core.data.dem

import com.nofar.core.database.dao.DemTileDao
import com.nofar.core.database.dao.TileCoverageDao
import com.nofar.core.model.DemTileId
import com.nofar.core.model.Region
import com.nofar.core.model.RegionBounds

object RegionDemTileResolver {
    /**
     * Tile IDs expected for a region from coverage rows, or intersecting tiles when coverage is empty.
     * Does not check on-disk readability — use [resolveTileIds] for openable tiles only.
     */
    suspend fun resolveExpectedTileIds(region: Region, tileCoverageDao: TileCoverageDao): List<String> {
        val fromCoverage = tileCoverageDao.getTileIdsForRegion(region.id.toString())
        return if (fromCoverage.isNotEmpty()) {
            fromCoverage
        } else {
            DemTileId.intersectingTiles(
                RegionBounds.boundingBox(
                    region.centerLat,
                    region.centerLon,
                    RegionBounds.dataCollectionRadiusM(region)
                )
            ).map { (tileLat, tileLon) -> DemTileId.fromCoordinates(tileLat, tileLon) }
        }
    }

    suspend fun resolveTileIds(
        region: Region,
        tileCoverageDao: TileCoverageDao,
        demTileDao: DemTileDao,
        tileReadable: (String) -> Boolean = { true }
    ): List<String> = resolveExpectedTileIds(region, tileCoverageDao).filter { candidateId ->
        demTileDao.getById(candidateId) != null && tileReadable(candidateId)
    }
}
