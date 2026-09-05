package com.nofar.core.data.usecase

import com.nofar.core.data.preferences.UserPreferencesRepository
import com.nofar.core.data.prepare.PrepareDownloadScheduler
import com.nofar.core.data.prepare.PrepareEstimator
import com.nofar.core.data.prepare.RegionNamePolicy
import com.nofar.core.data.repository.CoverageSetRepository
import com.nofar.core.data.repository.DemTileRepository
import com.nofar.core.database.dao.CoverageCellDao
import com.nofar.core.database.model.CoverageCellEntity
import com.nofar.core.model.AppConfig
import com.nofar.core.model.CellMembership
import com.nofar.core.model.CoverageSet
import com.nofar.core.model.DemTileId
import com.nofar.core.model.DownloadStatus
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class QuickCoverageDownloadUseCase
@Inject
constructor(
    private val coverageSetRepository: CoverageSetRepository,
    private val coverageCellDao: CoverageCellDao,
    private val demTileRepository: DemTileRepository,
    private val downloadScheduler: PrepareDownloadScheduler,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend fun syncAndEnqueue(coverageSet: CoverageSet, cellIds: List<String>): Result<UUID> = runCatching {
        val cells = cellIds.mapNotNull { DemTileId.parse(it) }
        require(cells.isNotEmpty()) { "No cells configured for coverage set" }
        val cacheLimit = userPreferencesRepository.demCacheLimitBytes.first()
        val demDisk = PrepareEstimator.estimateForCells(cells).demEstimateMinBytes
        val budget = (cacheLimit * AppConfig.COVERAGE_BYTE_BUDGET_CACHE_FRACTION).toLong()
        require(demDisk <= budget) {
            "Coverage DEM ($demDisk bytes) exceeds " +
                "${AppConfig.COVERAGE_BYTE_BUDGET_CACHE_FRACTION * 100}% of cache limit"
        }

        val existing = coverageSetRepository.getCoverageSet(coverageSet.id)
        if (existing == null) {
            coverageSetRepository.createCoverageSet(coverageSet)
        } else {
            coverageSetRepository.updateCoverageSet(coverageSet)
        }
        val coverageSetId = coverageSet.id.toString()
        val previousCellIds = coverageCellDao.getCellIdsForCoverageSet(coverageSetId).toSet()
        val nextCellIds = cellIds.toSet()
        val removed = previousCellIds - nextCellIds
        val added = nextCellIds - previousCellIds

        removed.forEach { tileId ->
            coverageCellDao.deleteForCoverageSetAndCell(coverageSetId, tileId)
            demTileRepository.decrementRefCount(tileId)
            if (demTileRepository.getTile(tileId)?.refCount == 0) {
                demTileRepository.evictTile(tileId)
            }
        }
        added.forEach { tileId ->
            val inserted = coverageCellDao.insert(CoverageCellEntity(coverageSetId, tileId))
            if (inserted != -1L && demTileRepository.getTile(tileId) != null) {
                demTileRepository.incrementRefCount(tileId)
            }
        }
        // Ensure full set is present (idempotent for overlapping replaces).
        coverageCellDao.insertAll(cellIds.map { cellId -> CoverageCellEntity(coverageSetId, cellId) })
        downloadScheduler.enqueue(coverageSet.id)
        coverageSet.id
    }

    suspend fun createAndEnqueueAtLocation(
        centerLat: Double,
        centerLon: Double,
        name: String = RegionNamePolicy.formatAutoName(centerLat, centerLon),
        existingCoverageSetId: UUID? = null,
        cellIds: List<String>? = null
    ): Result<UUID> {
        val resolvedCellIds =
            cellIds ?: CellMembership.localDownloadCellIds(centerLat, centerLon)
        val cells = resolvedCellIds.mapNotNull { DemTileId.parse(it) }
        val coverageSetId = existingCoverageSetId ?: UUID.randomUUID()
        val now = Instant.now()
        val estimate = PrepareEstimator.estimateForCells(cells)
        val existing = coverageSetRepository.getCoverageSet(coverageSetId)
        val labelLanguage = userPreferencesRepository.preferredLabelLanguage.first()
        val coverageSet =
            CoverageSet(
                id = coverageSetId,
                name = name.trim(),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                downloadStatus = existing?.downloadStatus ?: DownloadStatus.NOT_DOWNLOADED,
                downloadProgressPct = existing?.downloadProgressPct ?: 0,
                osmDatasetVersion = existing?.osmDatasetVersion,
                estimatedSizeBytes = estimate.totalEstimateBytes,
                entityCount = existing?.entityCount ?: 0,
                labelLanguage = labelLanguage
            )
        return syncAndEnqueue(coverageSet, resolvedCellIds)
    }
}
