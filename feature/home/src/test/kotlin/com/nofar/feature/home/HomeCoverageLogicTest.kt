package com.nofar.feature.home

import com.nofar.core.model.CellMembership
import com.nofar.core.model.CoverageSet
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.UserLocation
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCoverageLogicTest {
    @Test
    fun sortCoverageSetsForDisplay_withoutLocation_fallsBackToUpdatedAt() {
        val older = sampleCoverageSet(updatedAt = Instant.parse("2024-01-01T00:00:00Z"))
        val newer = sampleCoverageSet(updatedAt = Instant.parse("2025-01-01T00:00:00Z"))
        val sorted =
            HomeCoverageLogic.sortCoverageSetsForDisplay(
                listOf(older, newer),
                location = null,
                cellIdsBySet = emptyMap()
            )
        assertEquals(newer.id, sorted.first().id)
    }

    private fun userLocation(lat: Double, lon: Double): UserLocation = UserLocation(
        latitude = lat,
        longitude = lon,
        altitudeMeters = null,
        accuracyMeters = 10f,
        timestampMillis = 0L
    )

    @Test
    fun sortCoverageSetsForDisplay_insideCoverageSetsFirst() {
        val inside = sampleCoverageSet(updatedAt = Instant.parse("2024-01-01T00:00:00Z"))
        val outside = sampleCoverageSet(updatedAt = Instant.parse("2025-01-01T00:00:00Z"))
        val location = userLocation(lat = 32.5, lon = 35.5)
        val cellIdsBySet =
            mapOf(
                inside.id to setOf(CellMembership.cellIdForPoint(32.5, 35.5)),
                outside.id to setOf(CellMembership.cellIdForPoint(40.0, 40.0))
            )
        val sorted =
            HomeCoverageLogic.sortCoverageSetsForDisplay(
                listOf(outside, inside),
                location,
                cellIdsBySet
            )
        assertEquals(inside.id, sorted.first().id)
    }

    @Test
    fun shouldShowYouAreHere_onlyForEligibleInside() {
        val ready = sampleCoverageSet(downloadStatus = DownloadStatus.READY)
        assertTrue(HomeCoverageLogic.shouldShowYouAreHere(ready, isInside = true))
        assertFalse(HomeCoverageLogic.shouldShowYouAreHere(ready, isInside = false))
        val downloading = sampleCoverageSet(downloadStatus = DownloadStatus.DOWNLOADING)
        assertFalse(HomeCoverageLogic.shouldShowYouAreHere(downloading, isInside = true))
    }

    @Test
    fun isEnterExploreEnabled_onlyWhenInsideReadyCoverageSet() {
        assertFalse(HomeCoverageLogic.isEnterExploreEnabled(emptyList()))
        assertTrue(
            HomeCoverageLogic.isEnterExploreEnabled(
                listOf(sampleCoverageSet(downloadStatus = DownloadStatus.READY))
            )
        )
        assertTrue(
            HomeCoverageLogic.isEnterExploreEnabled(
                listOf(sampleCoverageSet(downloadStatus = DownloadStatus.PARTIAL))
            )
        )
    }

    @Test
    fun showsEmptyCoveragePrompt_onlyAfterLoadWithNoCoverageSets() {
        assertFalse(HomeCoverageLogic.showsEmptyCoveragePrompt(loading = true, coverageSetCount = 0))
        assertTrue(HomeCoverageLogic.showsEmptyCoveragePrompt(loading = false, coverageSetCount = 0))
        assertFalse(HomeCoverageLogic.showsEmptyCoveragePrompt(loading = false, coverageSetCount = 1))
    }

    @Test
    fun exploreEligibleInside_filtersByCellMembership() {
        val location = userLocation(32.5, 35.5)
        val ready = sampleCoverageSet(downloadStatus = DownloadStatus.READY)
        val outside = sampleCoverageSet(downloadStatus = DownloadStatus.READY)
        val cellIdsBySet =
            mapOf(
                ready.id to setOf(CellMembership.cellIdForPoint(32.5, 35.5)),
                outside.id to setOf(CellMembership.cellIdForPoint(40.0, 40.0))
            )
        val eligible = HomeCoverageLogic.exploreEligibleInside(listOf(ready, outside), location, cellIdsBySet)
        assertEquals(listOf(ready.id), eligible.map { it.id })
    }

    @Test
    fun resolveExploreNavigation_singleCoverageSet_navigatesDirectly() {
        val coverageSet = sampleCoverageSet()
        val decision = HomeCoverageLogic.resolveExploreNavigation(listOf(coverageSet))
        assertTrue(decision is ExploreNavigationDecision.Direct)
        assertEquals(coverageSet.id, (decision as ExploreNavigationDecision.Direct).coverageSetId)
    }

    @Test
    fun resolveExploreNavigation_prefersReadyOverPartial() {
        val partialNewer =
            sampleCoverageSet(
                downloadStatus = DownloadStatus.PARTIAL,
                updatedAt = Instant.parse("2026-01-02T00:00:00Z")
            )
        val readyOlder =
            sampleCoverageSet(
                downloadStatus = DownloadStatus.READY,
                updatedAt = Instant.parse("2026-01-01T00:00:00Z")
            )
        val decision = HomeCoverageLogic.resolveExploreNavigation(listOf(partialNewer, readyOlder))
        assertEquals(readyOlder.id, (decision as ExploreNavigationDecision.Direct).coverageSetId)
    }

    private fun sampleCoverageSet(
        id: UUID = UUID.randomUUID(),
        updatedAt: Instant = Instant.now(),
        downloadStatus: DownloadStatus = DownloadStatus.READY
    ): CoverageSet = CoverageSet(
        id = id,
        name = "Test",
        createdAt = updatedAt,
        updatedAt = updatedAt,
        downloadStatus = downloadStatus,
        downloadProgressPct = 100,
        osmDatasetVersion = null,
        estimatedSizeBytes = 0,
        entityCount = 0
    )
}
