package com.nofar.feature.prepare

import com.google.common.truth.Truth.assertThat
import com.nofar.core.data.prepare.PreparePhase
import com.nofar.core.data.prepare.PrepareProgress
import java.util.UUID
import org.junit.Test

class PrepareDownloadProgressReducerTest {
    private val trackedRegionId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val otherRegionId = UUID.fromString("22222222-2222-2222-2222-222222222222")

    @Test
    fun addRegionWithNullRegionId_ignoresBackgroundDownloadProgress() {
        val state = PrepareUiState(coverageSetId = null, step = PrepareStep.DEFINE)
        val progress =
            PrepareProgress(
                coverageSetId = otherRegionId,
                phase = PreparePhase.OSM,
                overallPercent = 20,
                message = "Downloading OSM data…"
            )

        val next = applyLiveDownloadProgress(state, progress)

        assertThat(next).isEqualTo(state)
        assertThat(next.downloadUiState).isEqualTo(PrepareDownloadUiState.IDLE)
        assertThat(next.step).isEqualTo(PrepareStep.DEFINE)
    }

    @Test
    fun progressForDifferentRegion_isIgnored() {
        val state =
            PrepareUiState(
                coverageSetId = trackedRegionId,
                step = PrepareStep.ESTIMATE,
                downloadUiState = PrepareDownloadUiState.IDLE
            )
        val progress =
            PrepareProgress(
                coverageSetId = otherRegionId,
                phase = PreparePhase.DEM,
                overallPercent = 55
            )

        val next = applyLiveDownloadProgress(state, progress)

        assertThat(next).isEqualTo(state)
    }

    @Test
    fun progressForTrackedRegion_switchesToDownloading() {
        val state =
            PrepareUiState(
                coverageSetId = trackedRegionId,
                step = PrepareStep.ESTIMATE,
                downloadUiState = PrepareDownloadUiState.IDLE
            )
        val progress =
            PrepareProgress(
                coverageSetId = trackedRegionId,
                phase = PreparePhase.OSM,
                overallPercent = 12,
                message = "Downloading OSM data…"
            )

        val next = applyLiveDownloadProgress(state, progress)

        assertThat(next.downloadUiState).isEqualTo(PrepareDownloadUiState.DOWNLOADING)
        assertThat(next.step).isEqualTo(PrepareStep.DOWNLOAD)
        assertThat(next.progress).isEqualTo(progress)
    }

    @Test
    fun completeOrError_keepsTerminalUiStateButUpdatesProgress() {
        val completeState =
            PrepareUiState(
                coverageSetId = trackedRegionId,
                downloadUiState = PrepareDownloadUiState.COMPLETE,
                step = PrepareStep.COMPLETE
            )
        val progress =
            PrepareProgress(
                coverageSetId = trackedRegionId,
                phase = PreparePhase.POST_PROCESSING,
                overallPercent = 100
            )

        val next = applyLiveDownloadProgress(completeState, progress)

        assertThat(next.downloadUiState).isEqualTo(PrepareDownloadUiState.COMPLETE)
        assertThat(next.step).isEqualTo(PrepareStep.COMPLETE)
        assertThat(next.progress).isEqualTo(progress)
    }

    @Test
    fun nullProgress_leavesStateUnchanged() {
        val state = PrepareUiState(coverageSetId = trackedRegionId)
        assertThat(applyLiveDownloadProgress(state, null)).isEqualTo(state)
    }
}
