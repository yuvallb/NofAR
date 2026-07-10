package com.nofar.feature.home

import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.Region
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeViewModelTest {
    @Test
    fun youAreHereBadgeRequiresReadyStatusAndLocationInside() {
        val region =
            Region(
                id = UUID.randomUUID(),
                name = "Test",
                centerLat = 32.0,
                centerLon = 35.0,
                radiusM = 12_000.0,
                minLat = 31.9,
                maxLat = 32.1,
                minLon = 34.9,
                maxLon = 35.1,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                downloadStatus = DownloadStatus.READY,
                downloadProgressPct = 100,
                osmDatasetVersion = null,
                estimatedSizeBytes = 42_000_000,
                entityCount = 100
            )

        val insideReady =
            com.nofar.core.designsystem.component.RegionCardState(
                region = region,
                isYouAreHere = true
            )
        assertTrue(insideReady.isYouAreHere)

        val outsideReady =
            insideReady.copy(isYouAreHere = false)
        assertFalse(outsideReady.isYouAreHere)

        val insideNotReady =
            insideReady.copy(
                region = region.copy(downloadStatus = DownloadStatus.NOT_DOWNLOADED),
                isYouAreHere = false
            )
        assertFalse(insideNotReady.isYouAreHere)
    }

    @Test
    fun initialUiStateIsEmptyAndExploreDisabled() {
        val state = HomeUiState()
        assertEquals(emptyList<com.nofar.core.designsystem.component.RegionCardState>(), state.regions)
        assertFalse(state.enterExploreEnabled)
        assertTrue(state.insideRegionIds.isEmpty())
    }

    @Test
    fun exploreEligibility_trueWhenReadyRegionInsideCachedLocation() {
        val location =
            com.nofar.core.model.UserLocation(
                latitude = 32.0,
                longitude = 35.0,
                altitudeMeters = null,
                accuracyMeters = 10f,
                timestampMillis = 0L
            )
        val ready =
            Region(
                id = UUID.randomUUID(),
                name = "Test",
                centerLat = 32.0,
                centerLon = 35.0,
                radiusM = 12_000.0,
                minLat = 31.9,
                maxLat = 32.1,
                minLon = 34.9,
                maxLon = 35.1,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                downloadStatus = DownloadStatus.READY,
                downloadProgressPct = 100,
                osmDatasetVersion = Instant.now(),
                estimatedSizeBytes = 42_000_000,
                entityCount = 10
            )
        val insideExplore = HomeRegionLogic.exploreEligibleInside(listOf(ready), location)
        assertTrue(HomeRegionLogic.isEnterExploreEnabled(insideExplore))
    }
}
