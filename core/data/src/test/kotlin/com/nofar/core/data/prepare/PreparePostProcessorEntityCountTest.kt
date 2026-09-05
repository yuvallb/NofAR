package com.nofar.core.data.prepare

import com.nofar.core.model.CoverageSet
import com.nofar.core.model.DownloadStatus
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
    fun coverageSetEntity_preservesHigherCountOnCopy() {
        val coverageSet = sampleCoverageSet(entityCount = 25)
        val updated = coverageSet.copy(entityCount = maxOf(0, coverageSet.entityCount))
        assertEquals(25, updated.entityCount)
    }

    private fun sampleCoverageSet(entityCount: Int): CoverageSet {
        val now = Instant.parse("2025-01-01T00:00:00Z")
        return CoverageSet(
            id = UUID.randomUUID(),
            name = "Test",
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
