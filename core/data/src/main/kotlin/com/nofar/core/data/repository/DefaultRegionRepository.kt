package com.nofar.core.data.repository

import com.nofar.core.database.dao.RegionDao
import com.nofar.core.database.model.asEntity
import com.nofar.core.database.model.asExternalModel
import com.nofar.core.model.Region
import com.nofar.core.model.RegionBounds
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultRegionRepository
@Inject
constructor(private val regionDao: RegionDao) : RegionRepository {
    override fun observeAllRegions(): Flow<List<Region>> =
        regionDao.observeAll().map { regions -> regions.map { it.asExternalModel() } }

    override suspend fun getRegion(id: UUID): Region? = regionDao.getById(id.toString())?.asExternalModel()

    override suspend fun createRegion(region: Region) {
        regionDao.upsert(region.asEntity())
    }

    override suspend fun updateRegion(region: Region) {
        regionDao.upsert(region.asEntity())
    }

    override suspend fun deleteRegion(id: UUID) {
        regionDao.deleteById(id.toString())
    }

    override suspend fun regionsContainingPoint(lat: Double, lon: Double): List<Region> = regionDao
        .getCandidatesContainingPoint(lat, lon)
        .map { it.asExternalModel() }
        .filter { RegionBounds.containsPoint(it, lat, lon) }
}
