package com.nofar.core.data.repository

import com.nofar.core.database.dao.DemTileDao
import com.nofar.core.database.dao.TileCoverageDao
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class HomeRegionMetadata(val demSizeBytes: Long, val latestDemTimestamp: Instant?)

class HomeRegionMetadataRepository
@Inject
constructor(
    private val tileCoverageDao: TileCoverageDao,
    private val demTileDao: DemTileDao
) {
    suspend fun getMetadata(regionId: UUID): HomeRegionMetadata {
        val tileIds = tileCoverageDao.getTileIdsForRegion(regionId.toString())
        if (tileIds.isEmpty()) {
            return HomeRegionMetadata(demSizeBytes = 0L, latestDemTimestamp = null)
        }
        var demSizeBytes = 0L
        var latestDemTimestamp: Instant? = null
        demTileDao.getByIds(tileIds).forEach { tile ->
            demSizeBytes += tile.sizeBytes
            val timestamp = Instant.ofEpochMilli(tile.lastAccessedAt)
            if (latestDemTimestamp == null || timestamp.isAfter(latestDemTimestamp)) {
                latestDemTimestamp = timestamp
            }
        }
        return HomeRegionMetadata(
            demSizeBytes = demSizeBytes,
            latestDemTimestamp = latestDemTimestamp
        )
    }
}
