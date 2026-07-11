package com.nofar.core.data.dem

import com.nofar.core.database.dao.DemTileDao
import com.nofar.core.database.dao.TileCoverageDao
import com.nofar.core.model.DemTileId
import com.nofar.core.model.Region
import com.nofar.core.model.RegionBounds

object RegionDemTileResolver {
    suspend fun resolveTileIds(
        region: Region,
        tileCoverageDao: TileCoverageDao,
        demTileDao: DemTileDao,
        tileReadable: (String) -> Boolean = { true }
    ): List<String> {
        val fromCoverage = tileCoverageDao.getTileIdsForRegion(region.id.toString())
        val candidates =
            if (fromCoverage.isNotEmpty()) {
                fromCoverage
            } else {
                DemTileId.intersectingTiles(
                    RegionBounds.boundingBox(region.centerLat, region.centerLon, region.radiusM)
                ).map { (tileLat, tileLon) -> DemTileId.fromCoordinates(tileLat, tileLon) }
            }
        return candidates.filter { candidateId ->
            demTileDao.getById(candidateId) != null && tileReadable(candidateId)
        }
    }
}
