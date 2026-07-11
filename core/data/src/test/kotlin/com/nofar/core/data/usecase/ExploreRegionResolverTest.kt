package com.nofar.core.data.usecase

import com.nofar.core.data.prepare.RegionNamePolicy
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.Region
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreRegionResolverTest {
    @Test
    fun readyRegionAtPoint_returnsActive() {
        val ready = sampleRegion(DownloadStatus.READY, updatedAt = Instant.parse("2026-01-02T00:00:00Z"))
        val resolution =
            ExploreRegionResolver.resolve(
                regionsAtPoint = listOf(ready),
                downloadingRegion = null,
                lat = 32.0,
                lon = 35.0
            )

        assertTrue(resolution is ExploreRegionResolution.Active)
        assertEquals(ready.id, (resolution as ExploreRegionResolution.Active).region.id)
    }

    @Test
    fun downloadingRegionAtPoint_returnsDownloading() {
        val downloading = sampleRegion(DownloadStatus.DOWNLOADING)
        val resolution =
            ExploreRegionResolver.resolve(
                regionsAtPoint = listOf(downloading),
                downloadingRegion = null,
                lat = 32.0,
                lon = 35.0
            )

        assertTrue(resolution is ExploreRegionResolution.Downloading)
    }

    @Test
    fun noRegionAtPoint_returnsNeedsDownloadProposal() {
        val resolution =
            ExploreRegionResolver.resolve(
                regionsAtPoint = emptyList(),
                downloadingRegion = null,
                lat = 32.0,
                lon = 35.0
            )

        assertTrue(resolution is ExploreRegionResolution.NeedsDownload)
        val proposal = (resolution as ExploreRegionResolution.NeedsDownload).proposal
        assertEquals(RegionNamePolicy.formatAutoName(32.0, 35.0), proposal.name)
    }

    @Test
    fun activeDownloadElsewhere_returnsDownloading() {
        val downloadingElsewhere = sampleRegion(DownloadStatus.DOWNLOADING)
        val resolution =
            ExploreRegionResolver.resolve(
                regionsAtPoint = emptyList(),
                downloadingRegion = downloadingElsewhere,
                lat = 32.0,
                lon = 35.0
            )

        assertTrue(resolution is ExploreRegionResolution.Downloading)
        assertEquals(downloadingElsewhere.id, (resolution as ExploreRegionResolution.Downloading).region.id)
    }

    private fun sampleRegion(status: DownloadStatus, updatedAt: Instant = Instant.now()): Region = Region(
        id = UUID.randomUUID(),
        name = "Test Region",
        centerLat = 32.0,
        centerLon = 35.0,
        radiusM = 10_000.0,
        minLat = 31.9,
        maxLat = 32.1,
        minLon = 34.9,
        maxLon = 35.1,
        createdAt = updatedAt,
        updatedAt = updatedAt,
        downloadStatus = status,
        downloadProgressPct = if (status == DownloadStatus.DOWNLOADING) 10 else 100,
        osmDatasetVersion = null,
        estimatedSizeBytes = 1L,
        entityCount = 1
    )
}
