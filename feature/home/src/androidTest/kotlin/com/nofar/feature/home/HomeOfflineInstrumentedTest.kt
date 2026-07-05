package com.nofar.feature.home

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.Region
import com.nofar.core.model.RegionBounds
import java.time.Instant
import java.util.UUID
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeOfflineInstrumentedTest {
    @Test
    fun homeUiState_buildsWithoutNetworkAccess() {
        val state = HomeUiState(loading = false)
        assertThat(state.regions).isEmpty()
        assertThat(state.enterExploreEnabled).isFalse()
    }

    @Test
    fun readFreeSpaceBytes_returnsNonNegativeFromLocalStorage() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertThat(readFreeSpaceBytes(context)).isAtLeast(0L)
    }

    @Test
    fun regionCardLogic_worksFullyOffline() {
        val region = offlineReadyRegion()
        val card =
            com.nofar.core.designsystem.component.RegionCardState(
                region = region,
                isYouAreHere = HomeRegionLogic.shouldShowYouAreHere(region, isInside = true),
                canEnterExplore = HomeRegionLogic.canEnterExplore(region, isInside = true)
            )
        assertThat(card.canEnterExplore).isTrue()
        assertThat(HomeRegionLogic.isEnterExploreEnabled(listOf(region))).isTrue()
    }

    private fun offlineReadyRegion(): Region {
        val box = RegionBounds.boundingBox(32.0, 35.0, 10_000.0)
        val now = Instant.now()
        return Region(
            id = UUID.randomUUID(),
            name = "Offline",
            centerLat = 32.0,
            centerLon = 35.0,
            radiusM = 10_000.0,
            minLat = box.minLat,
            maxLat = box.maxLat,
            minLon = box.minLon,
            maxLon = box.maxLon,
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
