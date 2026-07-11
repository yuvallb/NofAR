package com.nofar.core.data.repository

import com.nofar.core.data.dem.DemTileReader
import com.nofar.core.model.DemTile
import com.nofar.core.model.GeoEntity
import com.nofar.core.model.Region
import com.nofar.core.model.ResolutionLevel
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface RegionRepository {
    fun observeAllRegions(): Flow<List<Region>>

    suspend fun getRegion(id: UUID): Region?

    suspend fun createRegion(region: Region)

    suspend fun updateRegion(region: Region)

    suspend fun updateRegionName(id: UUID, name: String)

    suspend fun deleteRegion(id: UUID)

    suspend fun regionsContainingPoint(lat: Double, lon: Double): List<Region>

    suspend fun updateDownloadStatus(
        id: UUID,
        status: com.nofar.core.model.DownloadStatus,
        progressPct: Int,
        osmDatasetVersion: java.time.Instant? = null,
        entityCount: Int? = null
    )

    suspend fun hasActiveDownload(): Boolean
}

interface GeoEntityRepository {
    suspend fun getById(id: String): GeoEntity?

    suspend fun upsert(entity: GeoEntity): String

    suspend fun upsertFromStream(entities: Sequence<GeoEntity>)

    suspend fun queryWithinRadius(
        lat: Double,
        lon: Double,
        radiusM: Double,
        resolutionLevel: ResolutionLevel
    ): List<GeoEntity>

    suspend fun queryWithinRadiusForRegion(
        regionId: UUID,
        regionCenterLat: Double,
        regionCenterLon: Double,
        regionRadiusM: Double,
        lat: Double,
        lon: Double,
        radiusM: Double,
        resolutionLevel: ResolutionLevel
    ): List<GeoEntity>

    suspend fun garbageCollectOrphans(): Int
}

@Suppress("TooManyFunctions")
interface DemTileRepository {
    suspend fun registerTile(tile: DemTile)

    suspend fun getTile(tileId: String): DemTile?

    fun isBinReadable(tileId: String): Boolean

    suspend fun ensureRegisteredFromBin(tileId: String): Boolean

    fun openReader(tileId: String): DemTileReader?

    suspend fun incrementRefCount(tileId: String)

    suspend fun decrementRefCount(tileId: String)

    suspend fun totalCacheSizeBytes(): Long

    suspend fun evictTile(tileId: String): Boolean

    suspend fun getUnusedTiles(): List<DemTile>

    suspend fun getLruUnusedCandidates(): List<DemTile>

    suspend fun getAllLruCandidates(): List<DemTile>
}

data class StorageStats(
    val regionCount: Int,
    val entityDbSizeBytes: Long,
    val demCacheSizeBytes: Long,
    val entityRowCount: Int
)

interface StorageRepository {
    suspend fun getStorageStats(): StorageStats
}
