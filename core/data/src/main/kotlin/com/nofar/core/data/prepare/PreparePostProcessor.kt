package com.nofar.core.data.prepare

import com.nofar.core.database.dao.RegionDao
import com.nofar.core.database.dao.RegionEntityCoverageDao
import com.nofar.core.database.model.asEntity
import com.nofar.core.database.model.asExternalModel
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class PreparePostProcessor
@Inject
constructor(
    private val regionDao: RegionDao,
    private val regionEntityCoverageDao: RegionEntityCoverageDao,
    private val elevationFiller: MissingEntityElevationFiller
) {
    /**
     * @return true when every entity that still needed a DEM sample was filled successfully
     * (entities that already had OSM `ele` elevation are skipped and do not fail the result).
     */
    suspend fun process(regionId: UUID, onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }): Boolean {
        val entityIds = regionEntityCoverageDao.getEntityIdsForRegion(regionId.toString())
        val fillResult = elevationFiller.fill(entityIds, refreshDemSamples = true, onProgress = onProgress)

        val region = regionDao.getById(regionId.toString())?.asExternalModel() ?: return false
        val coverageCount = entityIds.size
        val finalCount = maxOf(coverageCount, region.entityCount)
        regionDao.upsert(
            region
                .copy(
                    entityCount = finalCount,
                    updatedAt = Instant.now()
                ).asEntity()
        )
        return fillResult.failed == 0
    }
}
