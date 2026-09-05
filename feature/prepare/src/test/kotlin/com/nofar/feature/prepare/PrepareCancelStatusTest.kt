package com.nofar.feature.prepare

import com.google.common.truth.Truth.assertThat
import com.nofar.core.data.prepare.PreparePhase
import com.nofar.core.data.prepare.PrepareProgress
import com.nofar.core.model.CoverageSet
import com.nofar.core.model.DownloadStatus
import java.time.Instant
import java.util.UUID
import org.junit.Test

class PrepareCancelStatusTest {
    @Test
    fun cancelWithNoData_returnsNotDownloaded() {
        val status =
            PrepareCancelStatus.resolve(
                region = sampleRegion(DownloadStatus.DOWNLOADING, entityCount = 0),
                progress = PrepareProgress(UUID.randomUUID(), PreparePhase.OSM, overallPercent = 10),
                hasTileCoverage = false,
                liveEntityCount = 0
            )
        assertThat(status).isEqualTo(DownloadStatus.NOT_DOWNLOADED)
    }

    @Test
    fun cancelAfterOsmEntities_returnsPartial() {
        val status =
            PrepareCancelStatus.resolve(
                region = sampleRegion(DownloadStatus.DOWNLOADING, entityCount = 12),
                progress = PrepareProgress(UUID.randomUUID(), PreparePhase.OSM, overallPercent = 35),
                hasTileCoverage = false,
                liveEntityCount = 12
            )
        assertThat(status).isEqualTo(DownloadStatus.PARTIAL)
    }

    @Test
    fun cancelAfterOsmPhaseComplete_returnsPartial() {
        val status =
            PrepareCancelStatus.resolve(
                region = sampleRegion(DownloadStatus.DOWNLOADING, entityCount = 0),
                progress = PrepareProgress(UUID.randomUUID(), PreparePhase.DEM, overallPercent = 50),
                hasTileCoverage = false,
                liveEntityCount = 0
            )
        assertThat(status).isEqualTo(DownloadStatus.PARTIAL)
    }

    @Test
    fun cancelWithTileCoverage_returnsPartial() {
        val status =
            PrepareCancelStatus.resolve(
                region = sampleRegion(DownloadStatus.DOWNLOADING, entityCount = 0),
                progress = PrepareProgress(UUID.randomUUID(), PreparePhase.OSM, overallPercent = 5),
                hasTileCoverage = true,
                liveEntityCount = 0
            )
        assertThat(status).isEqualTo(DownloadStatus.PARTIAL)
    }

    private fun sampleRegion(status: DownloadStatus, entityCount: Int): CoverageSet {
        val now = Instant.now()
        return CoverageSet(
            id = UUID.randomUUID(),
            name = "Test",
            createdAt = now,
            updatedAt = now,
            downloadStatus = status,
            downloadProgressPct = 20,
            osmDatasetVersion = null,
            estimatedSizeBytes = 1L,
            entityCount = entityCount
        )
    }
}
