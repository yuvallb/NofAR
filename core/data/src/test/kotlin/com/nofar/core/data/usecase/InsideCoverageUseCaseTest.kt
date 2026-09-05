package com.nofar.core.data.usecase

import com.nofar.core.data.repository.CoverageSetRepository
import com.nofar.core.model.CellMembership
import com.nofar.core.model.CoverageSet
import com.nofar.core.model.DownloadStatus
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InsideCoverageUseCaseTest {
    private val readyId = UUID.randomUUID()
    private val cellId = CellMembership.cellIdForPoint(32.5, 35.5)
    private val repository =
        object : CoverageSetRepository {
            private val ready = sampleCoverageSet(id = readyId)

            override fun observeAllCoverageSets() = flowOf(listOf(ready))

            override suspend fun getCoverageSet(id: UUID): CoverageSet? = if (id == ready.id) ready else null

            override suspend fun createCoverageSet(coverageSet: CoverageSet) = Unit

            override suspend fun updateCoverageSet(coverageSet: CoverageSet) = Unit

            override suspend fun updateCoverageSetName(id: UUID, name: String) = Unit

            override suspend fun deleteCoverageSet(id: UUID) = Unit

            override suspend fun coverageSetsContainingPoint(lat: Double, lon: Double): List<CoverageSet> =
                listOf(ready, sampleCoverageSet(id = UUID.randomUUID()))

            override suspend fun getCellIdsForCoverageSet(id: UUID): List<String> = listOf(cellId)

            override suspend fun getCellIdsForCoverageSets(ids: List<UUID>): List<String> =
                ids.flatMap { getCellIdsForCoverageSet(it) }

            override suspend fun updateDownloadStatus(
                id: UUID,
                status: DownloadStatus,
                progressPct: Int,
                osmDatasetVersion: Instant?,
                entityCount: Int?
            ) = Unit

            override suspend fun hasActiveDownload(): Boolean = false

            override suspend fun findDownloadingCoverageSet(): CoverageSet? = null
        }

    private val useCase = InsideCoverageUseCase(repository)

    @Test
    fun isInsideCoverageSet_pointInCell_returnsTrue() = runTest {
        val coverageSet = sampleCoverageSet(id = readyId)
        assertTrue(useCase.isInsideCoverageSet(coverageSet, 32.5, 35.5))
    }

    @Test
    fun isInsideCoverageSet_pointOutsideCell_returnsFalse() = runTest {
        val coverageSet = sampleCoverageSet(id = readyId)
        assertFalse(useCase.isInsideCoverageSet(coverageSet, 30.0, 30.0))
    }

    @Test
    fun exploreEligibleCoverageSetsContainingPoint_filtersNonReady() = runTest {
        val ready = sampleCoverageSet(downloadStatus = DownloadStatus.READY)
        val downloading = sampleCoverageSet(id = UUID.randomUUID(), downloadStatus = DownloadStatus.DOWNLOADING)
        val filteringRepository =
            object : CoverageSetRepository by repository {
                override suspend fun coverageSetsContainingPoint(lat: Double, lon: Double): List<CoverageSet> =
                    listOf(ready, downloading)
            }
        val filteringUseCase = InsideCoverageUseCase(filteringRepository)
        val eligible = filteringUseCase.exploreEligibleCoverageSetsContainingPoint(32.5, 35.5)
        assertTrue(eligible.all { useCase.isExploreEligible(it) })
        assertTrue(eligible.any { it.id == ready.id })
        assertFalse(eligible.any { it.id == downloading.id })
    }

    private fun sampleCoverageSet(
        id: UUID = UUID.randomUUID(),
        downloadStatus: DownloadStatus = DownloadStatus.READY
    ): CoverageSet {
        val now = Instant.now()
        return CoverageSet(
            id = id,
            name = "Test",
            createdAt = now,
            updatedAt = now,
            downloadStatus = downloadStatus,
            downloadProgressPct = 100,
            osmDatasetVersion = null,
            estimatedSizeBytes = 0,
            entityCount = 0
        )
    }
}
