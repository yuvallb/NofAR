package com.nofar.core.database.dao

import com.nofar.core.database.model.GeoEntityElevationMerge
import com.nofar.core.database.model.GeoEntityEntity
import com.nofar.core.database.model.RegionEntityCoverageEntity
import com.nofar.core.database.model.TileCoverageEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeoEntityUpserter
@Inject
constructor(private val geoEntityDao: GeoEntityDao) {
    suspend fun upsert(entity: GeoEntityEntity): Long {
        val existing = geoEntityDao.getByOsmId(entity.id)
        return if (existing == null) {
            val rowId = geoEntityDao.insert(entity)
            if (rowId != -1L) {
                rowId
            } else {
                val raced = geoEntityDao.getByOsmId(entity.id)
                    ?: error("geo_entity insert ignored but row missing for ${entity.id}")
                updateExisting(raced, entity)
                raced.rowId
            }
        } else {
            updateExisting(existing, entity)
            existing.rowId
        }
    }

    private suspend fun updateExisting(existing: GeoEntityEntity, entity: GeoEntityEntity) {
        val (elevation, elevationSource) =
            GeoEntityElevationMerge.resolve(
                incomingElevation = entity.elevation,
                incomingSource = entity.elevationSource,
                existingElevation = existing.elevation,
                existingSource = existing.elevationSource
            )
        geoEntityDao.updateByOsmId(
            osmId = entity.id,
            osmType = entity.osmType,
            name = entity.name,
            type = entity.type,
            lat = entity.lat,
            lon = entity.lon,
            elevation = elevation,
            elevationSource = elevationSource,
            lastSeenAt = entity.lastSeenAt,
            footprintRadiusM = entity.footprintRadiusM
        )
    }
}

@Singleton
class CoverageLinker
@Inject
constructor(
    private val regionEntityCoverageDao: RegionEntityCoverageDao,
    private val tileCoverageDao: TileCoverageDao,
    private val regionIngestDao: RegionIngestDao
) {
    /**
     * Persists a geo entity and its region coverage link in one Room [@Transaction]
     * (compatible with BundledSQLiteDriver).
     */
    suspend fun upsertAndLinkEntity(regionId: String, entity: GeoEntityEntity, displayName: String) {
        regionIngestDao.upsertAndLinkEntity(regionId, entity, displayName)
    }

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

    suspend fun linkTiles(regionId: String, tileIds: List<String>) {
        tileCoverageDao.insertAll(tileIds.map { TileCoverageEntity(regionId, it) })
    }
}
