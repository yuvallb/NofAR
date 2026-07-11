package com.nofar.core.data.usecase

import com.nofar.core.data.repository.RegionRepository
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.Region
import com.nofar.core.model.RegionBounds
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InsideRegionUseCaseTest {
    private val repository =
        object : RegionRepository {
            override fun observeAllRegions() = kotlinx.coroutines.flow.flowOf(emptyList<Region>())

            override suspend fun getRegion(id: UUID): Region? = null

            override suspend fun createRegion(region: Region) = Unit

            override suspend fun updateRegion(region: Region) = Unit

            override suspend fun updateRegionName(id: UUID, name: String) = Unit

            override suspend fun deleteRegion(id: UUID) = Unit

            override suspend fun regionsContainingPoint(lat: Double, lon: Double): List<Region> =
                listOf(sampleRegion(), sampleRegion(id = UUID.randomUUID(), centerLat = 33.0))

            override suspend fun updateDownloadStatus(
                id: UUID,
                status: DownloadStatus,
                progressPct: Int,
                osmDatasetVersion: Instant?,
                entityCount: Int?
            ) = Unit

            override suspend fun hasActiveDownload(): Boolean = false
        }

    private val useCase = InsideRegionUseCase(repository)

    @Test
    fun isInsideRegion_pointAtCenter_returnsTrue() {
        val region = sampleRegion()
        assertTrue(useCase.isInsideRegion(region, region.centerLat, region.centerLon))
    }

    @Test
    fun isInsideRegion_pointOutsideRadius_returnsFalse() {
        val region = sampleRegion(radiusM = 1_000.0)
        val distance =
            RegionBounds.haversineDistanceM(
                region.centerLat,
                region.centerLon,
                region.centerLat + 0.05,
                region.centerLon
            )
        assertTrue(distance > region.radiusM)
        assertFalse(useCase.isInsideRegion(region, region.centerLat + 0.05, region.centerLon))
    }

    @Test
    fun isInsideRegion_pointOnEdge_isInside() {
        val region = sampleRegion(radiusM = 10_000.0)
        val edgeLat = region.centerLat + (10_000.0 / 111_320.0)
        val distance =
            RegionBounds.haversineDistanceM(region.centerLat, region.centerLon, edgeLat, region.centerLon)
        assertTrue(distance <= region.radiusM + 1.0)
        assertTrue(useCase.isInsideRegion(region, edgeLat, region.centerLon))
    }

    @Test
    fun exploreEligibleRegionsContainingPoint_filtersNonReady() = runTest {
        val ready = sampleRegion(downloadStatus = DownloadStatus.READY)
        val downloading = sampleRegion(id = UUID.randomUUID(), downloadStatus = DownloadStatus.DOWNLOADING)
        val filteringRepository =
            object : RegionRepository by repository {
                override suspend fun regionsContainingPoint(lat: Double, lon: Double): List<Region> =
                    listOf(ready, downloading)
            }
        val filteringUseCase = InsideRegionUseCase(filteringRepository)
        val eligible = filteringUseCase.exploreEligibleRegionsContainingPoint(32.0, 35.0)
        assertTrue(eligible.all { useCase.isExploreEligible(it) })
        assertTrue(eligible.any { it.id == ready.id })
        assertFalse(eligible.any { it.id == downloading.id })
    }

    private fun sampleRegion(
        id: UUID = UUID.randomUUID(),
        centerLat: Double = 32.0,
        centerLon: Double = 35.0,
        radiusM: Double = 12_000.0,
        downloadStatus: DownloadStatus = DownloadStatus.READY
    ): Region {
        val box = RegionBounds.boundingBox(centerLat, centerLon, radiusM)
        val now = Instant.now()
        return Region(
            id = id,
            name = "Test",
            centerLat = centerLat,
            centerLon = centerLon,
            radiusM = radiusM,
            minLat = box.minLat,
            maxLat = box.maxLat,
            minLon = box.minLon,
            maxLon = box.maxLon,
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
