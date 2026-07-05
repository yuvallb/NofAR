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
    fun regionCardExploreRequiresReadyStatusAndLocationInside() {
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
                isYouAreHere = true,
                canEnterExplore = true
            )
        assertTrue(insideReady.canEnterExplore)

        val outsideReady =
            insideReady.copy(isYouAreHere = false, canEnterExplore = false)
        assertFalse(outsideReady.canEnterExplore)

        val insideNotReady =
            insideReady.copy(
                region = region.copy(downloadStatus = DownloadStatus.NOT_DOWNLOADED),
                canEnterExplore = false
            )
        assertFalse(insideNotReady.canEnterExplore)
    }

    @Test
    fun initialUiStateIsEmpty() {
        val state = HomeUiState()
        assertEquals(emptyList<com.nofar.core.designsystem.component.RegionCardState>(), state.regions)
    }
}
