package com.nofar.feature.prepare

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nofar.core.data.prepare.PreparePhase
import com.nofar.core.designsystem.component.PipelineStepState
import com.nofar.core.designsystem.util.NofARFormatters

internal fun pipelineStateForPhase(phase: PreparePhase?, activePhase: PreparePhase): PipelineStepState = when {
    phase == null -> PipelineStepState.PENDING
    phase == activePhase -> PipelineStepState.ACTIVE
    else -> PipelineStepState.COMPLETE
}

internal fun demPipelineState(phase: PreparePhase?): PipelineStepState = when {
    phase == null || phase == PreparePhase.OSM -> PipelineStepState.PENDING
    phase == PreparePhase.DEM -> PipelineStepState.ACTIVE
    else -> PipelineStepState.COMPLETE
}

internal fun postProcessPipelineState(
    phase: PreparePhase?,
    hasProgress: Boolean,
    overallPercent: Int = 0
): PipelineStepState = when {
    phase == PreparePhase.POST_PROCESSING && overallPercent >= 100 -> PipelineStepState.COMPLETE
    phase == PreparePhase.POST_PROCESSING -> PipelineStepState.ACTIVE
    hasProgress && phase != PreparePhase.OSM && phase != PreparePhase.DEM -> PipelineStepState.COMPLETE
    else -> PipelineStepState.PENDING
}

@Composable
internal fun osmDetailLines(phase: PreparePhase?, message: String?): List<String> {
    if (phase != PreparePhase.OSM) return emptyList()
    return listOf(message?.takeIf { it.isNotBlank() } ?: stringResource(R.string.prepare_step_streaming))
}

@Composable
internal fun demDetailLines(phase: PreparePhase?, remainingBytes: Long?, message: String? = null): List<String> {
    if (phase != PreparePhase.DEM) return emptyList()
    val detail = message?.takeIf { it.isNotBlank() }
    val formatted = NofARFormatters.formatMegabytes(remainingBytes ?: 0L)
    val eta = stringResource(R.string.prepare_step_eta, formatted)
    return if (detail != null) {
        listOf(detail, eta)
    } else {
        listOf(eta)
    }
}

internal fun phaseProgress(phase: PreparePhase?, activePhase: PreparePhase, overallPercent: Int?): Float? =
    if (phase == activePhase) overallPercent?.div(100f) else null
