package com.nofar.core.data.repository

import com.nofar.core.database.GeoEntitySpatialQuery
import com.nofar.core.database.dao.GeoEntityDao
import com.nofar.core.database.dao.GeoEntityUpserter
import com.nofar.core.database.model.asEntity
import com.nofar.core.database.model.asExternalModel
import com.nofar.core.model.GeoEntity
import com.nofar.core.model.ResolutionLevel
import javax.inject.Inject

class DefaultGeoEntityRepository
@Inject
constructor(
    private val geoEntityDao: GeoEntityDao,
    private val geoEntityUpserter: GeoEntityUpserter,
    private val spatialQuery: GeoEntitySpatialQuery
) : GeoEntityRepository {
    override suspend fun getById(id: String): GeoEntity? = geoEntityDao.getByOsmId(id)?.asExternalModel()

    override suspend fun upsert(entity: GeoEntity): String {
        geoEntityUpserter.upsert(entity.asEntity())
        return entity.id
    }

    override suspend fun upsertFromStream(entities: Sequence<GeoEntity>) {
        entities.forEach { entity ->
            geoEntityUpserter.upsert(entity.asEntity())
        }
    }

    override suspend fun queryWithinRadius(
        lat: Double,
        lon: Double,
        radiusM: Double,
        resolutionLevel: ResolutionLevel
    ): List<GeoEntity> = spatialQuery
        .queryWithinRadius(lat, lon, radiusM, resolutionLevel)
        .map { it.asExternalModel() }

    override suspend fun garbageCollectOrphans(): Int = geoEntityDao.deleteOrphans()
}
