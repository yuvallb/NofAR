package com.nofar.core.data.usecase

import com.nofar.core.data.prepare.RegionNamePolicy
import com.nofar.core.model.AppConfig
import com.nofar.core.model.CountryPackCatalog
import com.nofar.core.model.CoverageSet
import com.nofar.core.model.DownloadStatus
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreCoverageResolverTest {
    @Test
    fun readyCoverageSetAtPoint_returnsActive() {
        val ready = sampleCoverageSet(DownloadStatus.READY, updatedAt = Instant.parse("2026-01-02T00:00:00Z"))
        val resolution =
            ExploreCoverageResolver.resolve(
                coverageSetsAtPoint = listOf(ready),
                downloadingCoverageSet = null,
                lat = 32.0,
                lon = 35.0,
                cacheLimitBytes = AppConfig.DEM_CACHE_DEFAULT_LIMIT_BYTES
            )

        assertTrue(resolution is ExploreCoverageResolution.Active)
        assertEquals(ready.id, (resolution as ExploreCoverageResolution.Active).coverageSet.id)
    }

    @Test
    fun downloadingCoverageSetAtPoint_returnsDownloading() {
        val downloading = sampleCoverageSet(DownloadStatus.DOWNLOADING)
        val resolution =
            ExploreCoverageResolver.resolve(
                coverageSetsAtPoint = listOf(downloading),
                downloadingCoverageSet = null,
                lat = 32.0,
                lon = 35.0,
                cacheLimitBytes = AppConfig.DEM_CACHE_DEFAULT_LIMIT_BYTES
            )

        assertTrue(resolution is ExploreCoverageResolution.Downloading)
    }

    @Test
    fun noCoverageSetAtPoint_returnsNeedsDownloadProposal() {
        val resolution =
            ExploreCoverageResolver.resolve(
                coverageSetsAtPoint = emptyList(),
                downloadingCoverageSet = null,
                lat = 32.0,
                lon = 35.0,
                cacheLimitBytes = AppConfig.DEM_CACHE_DEFAULT_LIMIT_BYTES
            )

        assertTrue(resolution is ExploreCoverageResolution.NeedsDownload)
        val proposal = (resolution as ExploreCoverageResolution.NeedsDownload).proposal
        assertEquals(CountryPackCatalog.ISRAEL.displayName, proposal.name)
        assertEquals(CountryPackCatalog.ISRAEL.cellIds, proposal.cellIds)
    }

    @Test
    fun activeDownloadElsewhere_returnsDownloading() {
        val downloadingElsewhere = sampleCoverageSet(DownloadStatus.DOWNLOADING)
        val resolution =
            ExploreCoverageResolver.resolve(
                coverageSetsAtPoint = emptyList(),
                downloadingCoverageSet = downloadingElsewhere,
                lat = 32.0,
                lon = 35.0,
                cacheLimitBytes = AppConfig.DEM_CACHE_DEFAULT_LIMIT_BYTES
            )

        assertTrue(resolution is ExploreCoverageResolution.Downloading)
        assertEquals(
            downloadingElsewhere.id,
            (resolution as ExploreCoverageResolution.Downloading).coverageSet.id
        )
    }

    @Test
    fun noPackAtPoint_returnsLocalCellProposal() {
        val resolution =
            ExploreCoverageResolver.resolve(
                coverageSetsAtPoint = emptyList(),
                downloadingCoverageSet = null,
                lat = 48.8,
                lon = 2.3,
                cacheLimitBytes = AppConfig.DEM_CACHE_DEFAULT_LIMIT_BYTES
            )

        val proposal = (resolution as ExploreCoverageResolution.NeedsDownload).proposal
        assertEquals(RegionNamePolicy.formatAutoName(48.8, 2.3), proposal.name)
        assertTrue(proposal.cellIds.isNotEmpty())
    }

    private fun sampleCoverageSet(status: DownloadStatus, updatedAt: Instant = Instant.now()): CoverageSet =
        CoverageSet(
            id = UUID.randomUUID(),
            name = "Test Coverage",
            createdAt = updatedAt,
            updatedAt = updatedAt,
            downloadStatus = status,
            downloadProgressPct = if (status == DownloadStatus.DOWNLOADING) 10 else 100,
            osmDatasetVersion = null,
            estimatedSizeBytes = 1L,
            entityCount = 1
        )
}
