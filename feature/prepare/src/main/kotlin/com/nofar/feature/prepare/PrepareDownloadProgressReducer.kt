package com.nofar.feature.prepare

import com.nofar.core.data.prepare.PrepareProgress

/**
 * Applies singleton [PrepareDownloadOrchestrator] progress only when it belongs to the region
 * this Prepare screen is tracking. Without this filter, opening "Add Region" while another
 * download runs would jump straight to the downloading UI.
 */
internal fun applyLiveDownloadProgress(state: PrepareUiState, progress: PrepareProgress?): PrepareUiState {
    val belongsToTrackedRegion =
        progress != null && state.coverageSetId != null && state.coverageSetId == progress.coverageSetId
    if (!belongsToTrackedRegion) {
        return state
    }
    val terminal =
        state.downloadUiState == PrepareDownloadUiState.COMPLETE ||
            state.downloadUiState == PrepareDownloadUiState.ERROR
    return if (terminal) {
        state.copy(progress = progress)
    } else {
        state.copy(
            progress = progress,
            downloadUiState = PrepareDownloadUiState.DOWNLOADING,
            step = PrepareStep.DOWNLOAD
        )
    }
}
