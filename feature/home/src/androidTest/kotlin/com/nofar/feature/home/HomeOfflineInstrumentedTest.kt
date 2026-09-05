package com.nofar.feature.home

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.nofar.core.designsystem.component.CoverageSetCardState
import com.nofar.core.model.CellMembership
import com.nofar.core.model.CoverageSet
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.UserLocation
import java.time.Instant
import java.util.UUID
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeOfflineInstrumentedTest {
    @Test
    fun homeUiState_buildsWithoutNetworkAccess() {
        val state = HomeUiState(loading = false)
        assertThat(state.coverageSets).isEmpty()
        assertThat(state.enterExploreEnabled).isFalse()
    }

    @Test
    fun readFreeSpaceBytes_returnsNonNegativeFromLocalStorage() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertThat(readFreeSpaceBytes(context)).isAtLeast(0L)
    }

    @Test
    fun coverageSetCardLogic_worksFullyOffline() {
        val coverageSet = offlineReadyCoverageSet()
        val cellIds = setOf(CellMembership.cellIdForPoint(32.0, 35.0))
        val location =
            UserLocation(
                latitude = 32.0,
                longitude = 35.0,
                altitudeMeters = null,
                accuracyMeters = 10f,
                timestampMillis = 0L
            )
        val insideExplore =
            HomeCoverageLogic.exploreEligibleInside(
                coverageSets = listOf(coverageSet),
                location = location,
                cellIdsBySet = mapOf(coverageSet.id to cellIds)
            )
        val card =
            CoverageSetCardState(
                coverageSet = coverageSet,
                isYouAreHere = HomeCoverageLogic.shouldShowYouAreHere(coverageSet, isInside = true)
            )
        assertThat(card.isYouAreHere).isTrue()
        assertThat(HomeCoverageLogic.isEnterExploreEnabled(insideExplore)).isTrue()
    }

    private fun offlineReadyCoverageSet(): CoverageSet {
        val now = Instant.now()
        return CoverageSet(
            id = UUID.randomUUID(),
            name = "Offline",
            createdAt = now,
            updatedAt = now,
            downloadStatus = DownloadStatus.READY,
            downloadProgressPct = 100,
            osmDatasetVersion = now,
            estimatedSizeBytes = 42_000_000,
            entityCount = 10
        )
    }
}
