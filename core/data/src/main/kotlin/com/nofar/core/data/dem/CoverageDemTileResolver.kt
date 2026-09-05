package com.nofar.core.data.dem

import com.nofar.core.database.dao.DemTileDao
import com.nofar.core.model.DemTileId

object CoverageDemTileResolver {
    /** Cell ids map 1:1 to GLO-90 DEM tile ids. */
    fun cellIdsToTileIds(cellIds: List<String>): List<String> = cellIds.distinct()

    suspend fun resolveTileIds(
        cellIds: List<String>,
        demTileDao: DemTileDao,
        tileReadable: (String) -> Boolean = { true }
    ): List<String> = cellIdsToTileIds(cellIds).filter { candidateId ->
        demTileDao.getById(candidateId) != null && tileReadable(candidateId)
    }

    fun tileIdsFromCoordinates(cells: List<Pair<Int, Int>>): List<String> =
        cells.map { (tileLat, tileLon) -> DemTileId.fromCoordinates(tileLat, tileLon) }
}
