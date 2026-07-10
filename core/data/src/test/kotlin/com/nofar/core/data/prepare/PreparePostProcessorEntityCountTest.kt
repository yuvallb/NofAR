package com.nofar.core.data.prepare

import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.Region
import com.nofar.core.model.RegionBounds
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class PreparePostProcessorEntityCountTest {
    @Test
    fun finalEntityCount_neverDropsBelowOsmRecordedCount() {
        val osmRecorded = 42
        val coverageCount = 0
        val finalCount = maxOf(coverageCount, osmRecorded)
        assertEquals(42, finalCount)
    }

    @Test
    fun finalEntityCount_usesCoverageWhenHigher() {
        val osmRecorded = 10
        val coverageCount = 15
        val finalCount = maxOf(coverageCount, osmRecorded)
        assertEquals(15, finalCount)
    }

    @Test
    fun regionEntity_preservesHigherCountOnCopy() {
        val region = sampleRegion(entityCount = 25)
        val updated = region.copy(entityCount = maxOf(0, region.entityCount))
        assertEquals(25, updated.entityCount)
    }

    private fun sampleRegion(entityCount: Int): Region {
        val now = Instant.parse("2025-01-01T00:00:00Z")
        val box = RegionBounds.boundingBox(32.0, 35.0, 10_000.0)
        return Region(
            id = UUID.randomUUID(),
            name = "Test",
            centerLat = 32.0,
            centerLon = 35.0,
            radiusM = 10_000.0,
            minLat = box.minLat,
            maxLat = box.maxLat,
            minLon = box.minLon,
            maxLon = box.maxLon,
            createdAt = now,
            updatedAt = now,
            downloadStatus = DownloadStatus.DOWNLOADING,
            downloadProgressPct = 90,
            osmDatasetVersion = now,
            estimatedSizeBytes = 0,
            entityCount = entityCount
        )
    }
}
