package com.nofar.core.data.repository

import com.nofar.core.database.dao.CoverageCellDao
import com.nofar.core.database.dao.CoverageSetDao
import com.nofar.core.database.model.asEntity
import com.nofar.core.database.model.asExternalModel
import com.nofar.core.model.CellMembership
import com.nofar.core.model.CoverageSet
import com.nofar.core.model.DownloadStatus
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Suppress("TooManyFunctions")
class DefaultCoverageSetRepository
@Inject
constructor(
    private val coverageSetDao: CoverageSetDao,
    private val coverageCellDao: CoverageCellDao
) : CoverageSetRepository {
    override fun observeAllCoverageSets(): Flow<List<CoverageSet>> =
        coverageSetDao.observeAll().map { sets -> sets.map { it.asExternalModel() } }

    override suspend fun getCoverageSet(id: UUID): CoverageSet? =
        coverageSetDao.getById(id.toString())?.asExternalModel()

    override suspend fun createCoverageSet(coverageSet: CoverageSet) {
        coverageSetDao.upsert(coverageSet.asEntity())
    }

    override suspend fun updateCoverageSet(coverageSet: CoverageSet) {
        coverageSetDao.upsert(coverageSet.asEntity())
    }

    override suspend fun updateCoverageSetName(id: UUID, name: String) {
        coverageSetDao.updateName(id.toString(), name.trim(), Instant.now().toEpochMilli())
    }

    override suspend fun deleteCoverageSet(id: UUID) {
        coverageSetDao.deleteById(id.toString())
    }

    override suspend fun coverageSetsContainingPoint(lat: Double, lon: Double): List<CoverageSet> {
        val cellId = CellMembership.cellIdForPoint(lat, lon)
        val setIds = coverageCellDao.getCoverageSetIdsForCell(cellId)
        return setIds.mapNotNull { coverageSetDao.getById(it)?.asExternalModel() }
    }

    override suspend fun getCellIdsForCoverageSet(id: UUID): List<String> =
        coverageCellDao.getCellIdsForCoverageSet(id.toString())

    override suspend fun getCellIdsForCoverageSets(ids: List<UUID>): List<String> =
        coverageCellDao.getCellIdsForCoverageSets(ids.map { it.toString() })

    override suspend fun updateDownloadStatus(
        id: UUID,
        status: DownloadStatus,
        progressPct: Int,
        osmDatasetVersion: Instant?,
        entityCount: Int?
    ) {
        coverageSetDao.updateDownloadStatus(
            coverageSetId = id.toString(),
            status = status.name,
            progressPct = progressPct,
            updatedAt = Instant.now().toEpochMilli(),
            osmDatasetVersion = osmDatasetVersion?.toEpochMilli(),
            entityCount = entityCount
        )
    }

    override suspend fun hasActiveDownload(): Boolean =
        coverageSetDao.getAll().any { it.downloadStatus == DownloadStatus.DOWNLOADING.name }

    override suspend fun findDownloadingCoverageSet(): CoverageSet? = coverageSetDao.getAll()
        .firstOrNull { it.downloadStatus == DownloadStatus.DOWNLOADING.name }
        ?.asExternalModel()
}
