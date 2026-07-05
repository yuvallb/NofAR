package com.nofar.feature.prepare

import com.nofar.core.data.prepare.PreparePhase
import com.nofar.core.designsystem.component.PipelineStep
import com.nofar.core.designsystem.component.PipelineStepState

internal fun buildPipelineSteps(uiState: PrepareUiState): List<PipelineStep> = listOf(
    initPipelineStep(uiState),
    osmPipelineStep(uiState),
    demPipelineStep(uiState),
    postProcessPipelineStep(uiState),
    finalizePipelineStep(uiState)
)

private fun initPipelineStep(uiState: PrepareUiState): PipelineStep {
    val progress = uiState.progress
    return PipelineStep(
        title = "Initializing local database entries",
        state = if (progress != null) PipelineStepState.COMPLETE else PipelineStepState.ACTIVE
    )
}

private fun osmPipelineStep(uiState: PrepareUiState): PipelineStep {
    val progress = uiState.progress
    val phase = progress?.phase
    return PipelineStep(
        title = "Step 1: Querying OpenStreetMap Features",
        state = pipelineStateForPhase(phase, PreparePhase.OSM),
        detailLines = osmDetailLines(phase, progress?.message),
        progress = phaseProgress(phase, PreparePhase.OSM, progress?.overallPercent)
    )
}

private fun demPipelineStep(uiState: PrepareUiState): PipelineStep {
    val progress = uiState.progress
    val phase = progress?.phase
    val tileIndex = progress?.demTileIndex ?: 0
    val tileCount = progress?.demTileCount ?: uiState.demTileCount
    return PipelineStep(
        title = "Step 2: Downloading Elevation Tiles ($tileIndex/$tileCount)",
        state = demPipelineState(phase),
        detailLines = demDetailLines(phase, progress?.remainingBytesEstimate),
        progress = phaseProgress(phase, PreparePhase.DEM, progress?.overallPercent)
    )
}

private fun postProcessPipelineStep(uiState: PrepareUiState): PipelineStep {
    val progress = uiState.progress
    val phase = progress?.phase
    return PipelineStep(
        title = "Step 3: Converting Grids to Local Binary",
        state = postProcessPipelineState(phase, progress != null),
        detailLines =
        if (phase == PreparePhase.POST_PROCESSING) {
            listOf("Processing Copernicus GLO-30 matrix…")
        } else {
            emptyList()
        }
    )
}

private fun finalizePipelineStep(uiState: PrepareUiState): PipelineStep = PipelineStep(
    title = "Finalizing Region",
    state =
    if (uiState.downloadUiState == PrepareDownloadUiState.COMPLETE) {
        PipelineStepState.COMPLETE
    } else {
        PipelineStepState.PENDING
    }
)
