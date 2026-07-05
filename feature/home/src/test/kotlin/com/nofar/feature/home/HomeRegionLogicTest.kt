package com.nofar.feature.home

import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.Region
import com.nofar.core.model.RegionBounds
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRegionLogicTest {
    @Test
    fun sortRegionsByUpdatedAt_mostRecentFirst() {
        val older = sampleRegion(updatedAt = Instant.parse("2024-01-01T00:00:00Z"))
        val newer = sampleRegion(updatedAt = Instant.parse("2025-01-01T00:00:00Z"))
        val sorted = HomeRegionLogic.sortRegionsByUpdatedAt(listOf(older, newer))
        assertEquals(newer.id, sorted.first().id)
    }

    @Test
    fun shouldShowYouAreHere_onlyWhenInsideReadyOrPartial() {
        val ready = sampleRegion(downloadStatus = DownloadStatus.READY)
        assertTrue(HomeRegionLogic.shouldShowYouAreHere(ready, isInside = true))
        assertFalse(HomeRegionLogic.shouldShowYouAreHere(ready, isInside = false))
        val downloading = sampleRegion(downloadStatus = DownloadStatus.DOWNLOADING)
        assertFalse(HomeRegionLogic.shouldShowYouAreHere(downloading, isInside = true))
        val partial = sampleRegion(downloadStatus = DownloadStatus.PARTIAL)
        assertTrue(HomeRegionLogic.shouldShowYouAreHere(partial, isInside = true))
    }

    @Test
    fun isEnterExploreEnabled_onlyWhenInsideReadyRegion() {
        assertFalse(HomeRegionLogic.isEnterExploreEnabled(emptyList()))
        assertTrue(
            HomeRegionLogic.isEnterExploreEnabled(
                listOf(sampleRegion(downloadStatus = DownloadStatus.READY))
            )
        )
        assertTrue(
            HomeRegionLogic.isEnterExploreEnabled(
                listOf(sampleRegion(downloadStatus = DownloadStatus.PARTIAL))
            )
        )
    }

    @Test
    fun resolveExploreNavigation_singleRegion_navigatesDirectly() {
        val region = sampleRegion()
        val decision = HomeRegionLogic.resolveExploreNavigation(listOf(region))
        assertEquals(ExploreNavigationDecision.Direct(region.id), decision)
    }

    @Test
    fun resolveExploreNavigation_multipleRegions_showsOverlapPicker() {
        val first = sampleRegion()
        val second = sampleRegion(id = UUID.randomUUID())
        val decision = HomeRegionLogic.resolveExploreNavigation(listOf(first, second))
        assertEquals(ExploreNavigationDecision.OverlapPicker(listOf(first, second)), decision)
    }

    @Test
    fun resolveExploreNavigationForRegion_withOverlap_showsPicker() {
        val first = sampleRegion()
        val second = sampleRegion(id = UUID.randomUUID())
        val decision =
            HomeRegionLogic.resolveExploreNavigationForRegion(listOf(first, second), first.id)
        assertEquals(ExploreNavigationDecision.OverlapPicker(listOf(first, second)), decision)
    }

    @Test
    fun resolveExploreNavigationForRegion_outsideRegion_isDisabled() {
        val region = sampleRegion()
        val other = sampleRegion(id = UUID.randomUUID())
        val decision = HomeRegionLogic.resolveExploreNavigationForRegion(listOf(other), region.id)
        assertEquals(ExploreNavigationDecision.Disabled, decision)
    }

    @Test
    fun defaultOverlapSelection_usesLastSelectedWhenAvailable() {
        val first = sampleRegion()
        val second = sampleRegion(id = UUID.randomUUID())
        assertEquals(second.id, HomeRegionLogic.defaultOverlapSelection(listOf(first, second), second.id))
        assertEquals(first.id, HomeRegionLogic.defaultOverlapSelection(listOf(first, second), null))
    }

    private fun sampleRegion(
        id: UUID = UUID.randomUUID(),
        updatedAt: Instant = Instant.now(),
        downloadStatus: DownloadStatus = DownloadStatus.READY
    ): Region {
        val box = RegionBounds.boundingBox(32.0, 35.0, 12_000.0)
        return Region(
            id = id,
            name = "Test",
            centerLat = 32.0,
            centerLon = 35.0,
            radiusM = 12_000.0,
            minLat = box.minLat,
            maxLat = box.maxLat,
            minLon = box.minLon,
            maxLon = box.maxLon,
            createdAt = updatedAt,
            updatedAt = updatedAt,
            downloadStatus = downloadStatus,
            downloadProgressPct = 100,
            osmDatasetVersion = null,
            estimatedSizeBytes = 0,
            entityCount = 0
        )
    }
}
