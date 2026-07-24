package com.nofar.core.database.dao

import androidx.room.Transaction
import com.nofar.core.database.model.GeoEntityEntity
import com.nofar.core.database.model.RegionEntityCoverageEntity
import com.nofar.core.database.model.TileCoverageEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeoEntityUpserter
@Inject
constructor(private val geoEntityDao: GeoEntityDao) {
    @Transaction
    suspend fun upsert(entity: GeoEntityEntity): Long {
        val existing = geoEntityDao.getByOsmId(entity.id)
        return if (existing == null) {
            geoEntityDao.insert(entity)
        } else {
            geoEntityDao.updateByOsmId(
                osmId = entity.id,
                osmType = entity.osmType,
                name = entity.name,
                type = entity.type,
                lat = entity.lat,
                lon = entity.lon,
                elevation = entity.elevation,
                elevationSource = entity.elevationSource,
                lastSeenAt = entity.lastSeenAt,
                footprintRadiusM = entity.footprintRadiusM
            )
            existing.rowId
        }
    }
}

@Singleton
class CoverageLinker
@Inject
constructor(
    private val regionEntityCoverageDao: RegionEntityCoverageDao,
    private val tileCoverageDao: TileCoverageDao
) {
    @Transaction
    suspend fun linkEntities(regionId: String, entities: List<Pair<String, String>>) {
        regionEntityCoverageDao.insertAll(
            entities.map { (entityId, displayName) ->
                RegionEntityCoverageEntity(
                    regionId = regionId,
                    entityId = entityId,
                    displayName = displayName
                )
            }
        )
    }

    @Transaction
    suspend fun linkTiles(regionId: String, tileIds: List<String>) {
        tileCoverageDao.insertAll(tileIds.map { TileCoverageEntity(regionId, it) })
    }
}
