package com.nofar.core.data.prepare

import com.nofar.core.database.dao.CoverageEntityDao
import com.nofar.core.database.dao.CoverageSetDao
import com.nofar.core.database.model.asEntity
import com.nofar.core.database.model.asExternalModel
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class PreparePostProcessor
@Inject
constructor(
    private val coverageSetDao: CoverageSetDao,
    private val coverageEntityDao: CoverageEntityDao,
    private val elevationFiller: MissingEntityElevationFiller
) {
    suspend fun process(coverageSetId: UUID, onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }): Boolean {
        val entityIds = coverageEntityDao.getEntityIdsForCoverageSet(coverageSetId.toString())
        val fillResult = elevationFiller.fill(entityIds, refreshDemSamples = true, onProgress = onProgress)

        val coverageSet = coverageSetDao.getById(coverageSetId.toString())?.asExternalModel() ?: return false
        val coverageCount = entityIds.size
        val finalCount = maxOf(coverageCount, coverageSet.entityCount)
        coverageSetDao.upsert(
            coverageSet
                .copy(
                    entityCount = finalCount,
                    updatedAt = Instant.now()
                ).asEntity()
        )
        return fillResult.failed == 0
    }
}
