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
        detailLines = demDetailLines(phase, progress?.remainingBytesEstimate, progress?.message),
        progress = phaseProgress(phase, PreparePhase.DEM, progress?.overallPercent)
    )
}

private fun postProcessPipelineStep(uiState: PrepareUiState): PipelineStep {
    val progress = uiState.progress
    val phase = progress?.phase
    return PipelineStep(
        title = "Step 3: Enriching Feature Elevations",
        state = postProcessPipelineState(phase, progress != null, progress?.overallPercent ?: 0),
        detailLines =
        if (phase == PreparePhase.POST_PROCESSING) {
            listOf(
                progress?.message?.takeIf { it.isNotBlank() }
                    ?: "Sampling elevations from downloaded DEM tiles…"
            )
        } else {
            emptyList()
        }
    )
}

private fun finalizePipelineStep(uiState: PrepareUiState): PipelineStep {
    val progress = uiState.progress
    return PipelineStep(
        title = "Finalizing Region",
        state =
        when {
            uiState.downloadUiState == PrepareDownloadUiState.COMPLETE -> PipelineStepState.COMPLETE
            progress?.phase == PreparePhase.POST_PROCESSING && progress.overallPercent >= 100 ->
                PipelineStepState.ACTIVE
            else -> PipelineStepState.PENDING
        },
        detailLines =
        if (progress?.phase == PreparePhase.POST_PROCESSING && progress.overallPercent >= 100) {
            listOf(progress.message.ifBlank { "Finalizing…" })
        } else {
            emptyList()
        }
    )
}
