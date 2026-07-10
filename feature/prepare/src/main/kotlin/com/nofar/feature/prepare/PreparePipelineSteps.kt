package com.nofar.feature.prepare

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nofar.core.data.prepare.PreparePhase
import com.nofar.core.designsystem.component.PipelineStep
import com.nofar.core.designsystem.component.PipelineStepState

@Composable
internal fun buildPipelineSteps(uiState: PrepareUiState): List<PipelineStep> = listOf(
    initPipelineStep(uiState),
    osmPipelineStep(uiState),
    demPipelineStep(uiState),
    postProcessPipelineStep(uiState),
    finalizePipelineStep(uiState)
)

@Composable
private fun initPipelineStep(uiState: PrepareUiState): PipelineStep {
    val progress = uiState.progress
    return PipelineStep(
        title = stringResource(R.string.prepare_step_init),
        state = if (progress != null) PipelineStepState.COMPLETE else PipelineStepState.ACTIVE
    )
}

@Composable
private fun osmPipelineStep(uiState: PrepareUiState): PipelineStep {
    val progress = uiState.progress
    val phase = progress?.phase
    return PipelineStep(
        title = stringResource(R.string.prepare_step_osm),
        state = pipelineStateForPhase(phase, PreparePhase.OSM),
        detailLines = osmDetailLines(phase, progress?.message),
        progress = phaseProgress(phase, PreparePhase.OSM, progress?.overallPercent)
    )
}

@Composable
private fun demPipelineStep(uiState: PrepareUiState): PipelineStep {
    val progress = uiState.progress
    val phase = progress?.phase
    val tileIndex = progress?.demTileIndex ?: 0
    val tileCount = progress?.demTileCount ?: uiState.demTileCount
    return PipelineStep(
        title = stringResource(R.string.prepare_step_dem, tileIndex, tileCount),
        state = demPipelineState(phase),
        detailLines = demDetailLines(phase, progress?.remainingBytesEstimate, progress?.message),
        progress = phaseProgress(phase, PreparePhase.DEM, progress?.overallPercent)
    )
}

@Composable
private fun postProcessPipelineStep(uiState: PrepareUiState): PipelineStep {
    val progress = uiState.progress
    val phase = progress?.phase
    val context = LocalContext.current
    val defaultDetail = stringResource(R.string.prepare_step_enrich_detail)
    val resolvedMessage = progress?.message?.asString(context)?.takeIf { it.isNotBlank() }
    return PipelineStep(
        title = stringResource(R.string.prepare_step_enrich),
        state = postProcessPipelineState(phase, progress != null, progress?.overallPercent ?: 0),
        detailLines =
        if (phase == PreparePhase.POST_PROCESSING) {
            listOf(resolvedMessage ?: defaultDetail)
        } else {
            emptyList()
        }
    )
}

@Composable
private fun finalizePipelineStep(uiState: PrepareUiState): PipelineStep {
    val progress = uiState.progress
    val context = LocalContext.current
    val defaultDetail = stringResource(R.string.prepare_step_finalize_detail)
    val resolvedMessage = progress?.message?.asString(context).orEmpty()
    return PipelineStep(
        title = stringResource(R.string.prepare_step_finalize),
        state =
        when {
            uiState.downloadUiState == PrepareDownloadUiState.COMPLETE -> PipelineStepState.COMPLETE
            progress?.phase == PreparePhase.POST_PROCESSING && progress.overallPercent >= 100 ->
                PipelineStepState.ACTIVE
            else -> PipelineStepState.PENDING
        },
        detailLines =
        if (progress?.phase == PreparePhase.POST_PROCESSING && progress.overallPercent >= 100) {
            listOf(resolvedMessage.ifBlank { defaultDetail })
        } else {
            emptyList()
        }
    )
}
