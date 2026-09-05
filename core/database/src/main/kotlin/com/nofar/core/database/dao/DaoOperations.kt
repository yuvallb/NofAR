package com.nofar.core.database.dao

import com.nofar.core.database.model.CoverageCellEntity
import com.nofar.core.database.model.CoverageEntityEntity
import com.nofar.core.database.model.GeoEntityElevationMerge
import com.nofar.core.database.model.GeoEntityEntity
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
    private val coverageEntityDao: CoverageEntityDao,
    private val coverageCellDao: CoverageCellDao,
    private val coverageIngestDao: CoverageIngestDao
) {
    suspend fun upsertAndLinkEntity(coverageSetId: String, entity: GeoEntityEntity, displayName: String) {
        coverageIngestDao.upsertAndLinkEntity(coverageSetId, entity, displayName)
    }

    suspend fun linkEntities(coverageSetId: String, entities: List<Pair<String, String>>) {
        coverageEntityDao.insertAll(
            entities.map { (entityId, displayName) ->
                CoverageEntityEntity(
                    coverageSetId = coverageSetId,
                    entityId = entityId,
                    displayName = displayName
                )
            }
        )
    }

    suspend fun linkCells(coverageSetId: String, cellIds: List<String>) {
        coverageCellDao.insertAll(cellIds.map { cellId -> CoverageCellEntity(coverageSetId, cellId) })
    }
}
