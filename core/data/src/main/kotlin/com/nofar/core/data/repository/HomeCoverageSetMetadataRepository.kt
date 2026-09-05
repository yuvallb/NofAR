package com.nofar.core.data.repository

import com.nofar.core.data.dem.CoverageDemTileResolver
import com.nofar.core.database.dao.CoverageCellDao
import com.nofar.core.database.dao.CoverageEntityDao
import com.nofar.core.database.dao.DemTileDao
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class HomeCoverageSetMetadata(
    val demSizeBytes: Long,
    val latestDemTimestamp: Instant?,
    val liveEntityCount: Int = 0,
    val tileCount: Int = 0
)

class HomeCoverageSetMetadataRepository
@Inject
constructor(
    private val coverageCellDao: CoverageCellDao,
    private val demTileDao: DemTileDao,
    private val coverageEntityDao: CoverageEntityDao
) {
    suspend fun getMetadata(coverageSetId: UUID, cellIds: List<String>? = null): HomeCoverageSetMetadata {
        val resolvedCellIds = cellIds ?: coverageCellDao.getCellIdsForCoverageSet(coverageSetId.toString())
        val tileIds = CoverageDemTileResolver.cellIdsToTileIds(resolvedCellIds)
        val liveEntityCount = coverageEntityDao.getEntityIdsForCoverageSet(coverageSetId.toString()).size
        val tiles = demTileDao.getByIds(tileIds)
        if (tiles.isEmpty()) {
            return HomeCoverageSetMetadata(
                demSizeBytes = 0L,
                latestDemTimestamp = null,
                liveEntityCount = liveEntityCount,
                tileCount = 0
            )
        }
        var demSizeBytes = 0L
        var latestDemTimestamp: Instant? = null
        tiles.forEach { tile ->
            demSizeBytes += tile.sizeBytes
            val timestamp = Instant.ofEpochMilli(tile.lastAccessedAt)
            if (latestDemTimestamp == null || timestamp.isAfter(latestDemTimestamp)) {
                latestDemTimestamp = timestamp
            }
        }
        return HomeCoverageSetMetadata(
            demSizeBytes = demSizeBytes,
            latestDemTimestamp = latestDemTimestamp,
            liveEntityCount = liveEntityCount,
            tileCount = tiles.size
        )
    }
}
