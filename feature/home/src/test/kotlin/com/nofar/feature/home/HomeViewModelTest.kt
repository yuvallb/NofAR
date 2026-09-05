package com.nofar.feature.home

import com.nofar.core.designsystem.component.CoverageSetCardState
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

class HomeViewModelTest {
    @Test
    fun youAreHereBadgeRequiresReadyStatusAndLocationInside() {
        val coverageSet = sampleCoverageSet(downloadStatus = DownloadStatus.READY)

        val insideReady =
            CoverageSetCardState(
                coverageSet = coverageSet,
                isYouAreHere = true
            )
        assertTrue(insideReady.isYouAreHere)

        val outsideReady = insideReady.copy(isYouAreHere = false)
        assertFalse(outsideReady.isYouAreHere)

        val insideNotReady =
            insideReady.copy(
                coverageSet = coverageSet.copy(downloadStatus = DownloadStatus.NOT_DOWNLOADED),
                isYouAreHere = false
            )
        assertFalse(insideNotReady.isYouAreHere)
    }

    @Test
    fun initialUiStateIsEmptyAndExploreDisabled() {
        val state = HomeUiState()
        assertEquals(emptyList<CoverageSetCardState>(), state.coverageSets)
        assertFalse(state.enterExploreEnabled)
        assertTrue(state.insideCoverageSetIds.isEmpty())
    }

    @Test
    fun exploreEligibility_trueWhenReadyCoverageInsideCachedLocation() {
        val location =
            UserLocation(
                latitude = 32.5,
                longitude = 35.5,
                altitudeMeters = null,
                accuracyMeters = 10f,
                timestampMillis = 0L
            )
        val ready = sampleCoverageSet(downloadStatus = DownloadStatus.READY)
        val cellIdsBySet = mapOf(ready.id to setOf(CellMembership.cellIdForPoint(32.5, 35.5)))
        val insideExplore = HomeCoverageLogic.exploreEligibleInside(listOf(ready), location, cellIdsBySet)
        assertTrue(HomeCoverageLogic.isEnterExploreEnabled(insideExplore))
    }

    private fun sampleCoverageSet(downloadStatus: DownloadStatus): CoverageSet {
        val now = Instant.now()
        return CoverageSet(
            id = UUID.randomUUID(),
            name = "Test",
            createdAt = now,
            updatedAt = now,
            downloadStatus = downloadStatus,
            downloadProgressPct = 100,
            osmDatasetVersion = null,
            estimatedSizeBytes = 42_000_000,
            entityCount = 100
        )
    }
}
