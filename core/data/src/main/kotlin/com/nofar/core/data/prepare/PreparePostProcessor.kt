@file:Suppress("LoopWithTooManyJumpStatements")

package com.nofar.core.data.prepare

import com.nofar.core.data.dem.DemTileReader
import com.nofar.core.data.repository.DefaultDemTileRepository
import com.nofar.core.data.repository.GeoEntityRepository
import com.nofar.core.database.dao.RegionDao
import com.nofar.core.database.dao.RegionEntityCoverageDao
import com.nofar.core.database.model.asEntity
import com.nofar.core.database.model.asExternalModel
import com.nofar.core.model.DemTileId
import com.nofar.core.model.ElevationSource
import com.nofar.core.model.GeoEntity
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class PreparePostProcessor
@Inject
constructor(
    private val regionDao: RegionDao,
    private val regionEntityCoverageDao: RegionEntityCoverageDao,
    private val geoEntityRepository: GeoEntityRepository,
    private val demTileRepository: DefaultDemTileRepository
) {
    suspend fun process(regionId: UUID, onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }): Boolean {
        val entityIds = regionEntityCoverageDao.getEntityIdsForRegion(regionId.toString())
        val total = entityIds.size
        val readers = mutableMapOf<String, DemTileReader>()
        var allSucceeded = true

        try {
            for ((index, entityId) in entityIds.withIndex()) {
                onProgress(index + 1, total)

                val entity = geoEntityRepository.getById(entityId) ?: continue
                if (entity.elevation != null) continue

                val sampled = sampleElevation(entity, readers)
                if (sampled == null) {
                    allSucceeded = false
                    continue
                }

                geoEntityRepository.upsert(
                    entity.copy(
                        elevation = sampled.toDouble(),
                        elevationSource = ElevationSource.DEM_SAMPLE,
                        lastSeenAt = Instant.now()
                    )
                )
            }
        } finally {
            readers.values.forEach(DemTileReader::close)
        }

        val region = regionDao.getById(regionId.toString())?.asExternalModel() ?: return false
        regionDao.upsert(
            region
                .copy(
                    entityCount = entityIds.size,
                    updatedAt = Instant.now()
                ).asEntity()
        )
        return allSucceeded
    }

    private fun sampleElevation(entity: GeoEntity, readers: MutableMap<String, DemTileReader>): Float? {
        val tileCoords = DemTileId.coordinatesForPoint(entity.lat, entity.lon)
        val tileId = DemTileId.fromCoordinates(tileCoords.first, tileCoords.second)
        val reader =
            readers[tileId] ?: demTileRepository.openReader(tileId)?.also { opened ->
                readers[tileId] = opened
            } ?: return null
        return reader.elevationAt(entity.lat, entity.lon)
    }
}
