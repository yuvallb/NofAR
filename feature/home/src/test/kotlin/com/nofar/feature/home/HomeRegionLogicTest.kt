package com.nofar.feature.home

import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.Region
import com.nofar.core.model.RegionBounds
import com.nofar.core.model.UserLocation
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRegionLogicTest {
    @Test
    fun sortRegionsForDisplay_withoutLocation_fallsBackToUpdatedAt() {
        val older = sampleRegion(updatedAt = Instant.parse("2024-01-01T00:00:00Z"))
        val newer = sampleRegion(updatedAt = Instant.parse("2025-01-01T00:00:00Z"))
        val sorted = HomeRegionLogic.sortRegionsForDisplay(listOf(older, newer), location = null)
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
    fun sortRegionsForDisplay_insideRegionsFirst() {
        val inside =
            sampleRegion(
                centerLat = 32.0,
                centerLon = 35.0,
                updatedAt = Instant.parse("2024-01-01T00:00:00Z")
            )
        val outside =
            sampleRegion(
                centerLat = 33.0,
                centerLon = 36.0,
                updatedAt = Instant.parse("2025-01-01T00:00:00Z")
            )
        val location = userLocation(lat = 32.0, lon = 35.0)
        val sorted = HomeRegionLogic.sortRegionsForDisplay(listOf(outside, inside), location)
        assertEquals(inside.id, sorted.first().id)
    }

    @Test
    fun sortRegionsForDisplay_insideRegionsSortedByProximity() {
        val nearer =
            sampleRegion(
                centerLat = 32.01,
                centerLon = 35.01,
                updatedAt = Instant.parse("2024-01-01T00:00:00Z")
            )
        val farther =
            sampleRegion(
                centerLat = 32.05,
                centerLon = 35.05,
                updatedAt = Instant.parse("2025-01-01T00:00:00Z")
            )
        val location = userLocation(lat = 32.0, lon = 35.0)
        val sorted = HomeRegionLogic.sortRegionsForDisplay(listOf(farther, nearer), location)
        assertEquals(nearer.id, sorted.first().id)
    }

    @Test
    fun sortRegionsForDisplay_outsideRegionsSortedByProximity() {
        val nearerOutside =
            sampleRegion(
                centerLat = 32.5,
                centerLon = 35.5,
                updatedAt = Instant.parse("2024-01-01T00:00:00Z")
            )
        val fartherOutside =
            sampleRegion(
                centerLat = 33.0,
                centerLon = 36.0,
                updatedAt = Instant.parse("2025-01-01T00:00:00Z")
            )
        val location = userLocation(lat = 32.0, lon = 35.0)
        val sorted = HomeRegionLogic.sortRegionsForDisplay(listOf(fartherOutside, nearerOutside), location)
        assertEquals(nearerOutside.id, sorted.first().id)
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
    fun exploreEligibleInside_requiresReadyStatusAndLocationInside() {
        val location = userLocation(lat = 32.0, lon = 35.0)
        val ready = sampleRegion(downloadStatus = DownloadStatus.READY)
        val downloading = sampleRegion(downloadStatus = DownloadStatus.DOWNLOADING)
        val outside = sampleRegion(centerLat = 33.0, centerLon = 36.0, downloadStatus = DownloadStatus.READY)

        val eligible = HomeRegionLogic.exploreEligibleInside(listOf(ready, downloading, outside), location)
        assertEquals(listOf(ready.id), eligible.map { it.id })
    }

    @Test
    fun exploreEligibleInside_enablesExploreWhenRegionBecomesReadyWithoutNewGpsFix() {
        val location = userLocation(lat = 32.0, lon = 35.0)
        val ready = sampleRegion(downloadStatus = DownloadStatus.READY)
        val notReady = sampleRegion(downloadStatus = DownloadStatus.DOWNLOADING)

        val notReadyEligible = HomeRegionLogic.exploreEligibleInside(listOf(notReady), location)
        assertFalse(HomeRegionLogic.isEnterExploreEnabled(notReadyEligible))
        val readyEligible = HomeRegionLogic.exploreEligibleInside(listOf(ready), location)
        assertTrue(HomeRegionLogic.isEnterExploreEnabled(readyEligible))
    }

    @Test
    fun exploreEligibleInside_returnsEmptyWithoutLocation() {
        val ready = sampleRegion(downloadStatus = DownloadStatus.READY)
        assertTrue(HomeRegionLogic.exploreEligibleInside(listOf(ready), location = null).isEmpty())
    }

    @Test
    fun resolveExploreNavigation_singleRegion_navigatesDirectly() {
        val region = sampleRegion()
        val decision = HomeRegionLogic.resolveExploreNavigation(listOf(region))
        assertEquals(ExploreNavigationDecision.Direct(region.id), decision)
    }

    @Test
    fun resolveExploreNavigation_multipleRegions_navigatesToNewest() {
        val older = sampleRegion(updatedAt = Instant.parse("2024-01-01T00:00:00Z"))
        val newer =
            sampleRegion(
                id = UUID.randomUUID(),
                updatedAt = Instant.parse("2024-06-01T00:00:00Z")
            )
        val decision = HomeRegionLogic.resolveExploreNavigation(listOf(older, newer))
        assertEquals(ExploreNavigationDecision.Direct(newer.id), decision)
    }

    private fun sampleRegion(
        id: UUID = UUID.randomUUID(),
        centerLat: Double = 32.0,
        centerLon: Double = 35.0,
        updatedAt: Instant = Instant.now(),
        downloadStatus: DownloadStatus = DownloadStatus.READY
    ): Region {
        val box = RegionBounds.boundingBox(centerLat, centerLon, 12_000.0)
        return Region(
            id = id,
            name = "Test",
            centerLat = centerLat,
            centerLon = centerLon,
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
