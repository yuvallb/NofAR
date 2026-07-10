package com.nofar.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nofar.core.designsystem.R
import com.nofar.core.designsystem.theme.NofARColors
import com.nofar.core.designsystem.util.NofARFormatters

enum class PipelineStepState {
    COMPLETE,
    ACTIVE,
    PENDING
}

data class PipelineStep(
    val title: String,
    val state: PipelineStepState,
    val detailLines: List<String> = emptyList(),
    val progress: Float? = null
)

@Composable
fun NofARDownloadPipeline(
    regionName: String,
    steps: List<PipelineStep>,
    overallPercent: Int,
    estimatedTimeRemaining: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.pipeline_downloading, regionName),
            style = MaterialTheme.typography.titleLarge,
            color = NofARColors.TextPrimary,
            fontWeight = FontWeight.Bold
        )

        steps.forEach { step ->
            PipelineStepRow(step = step)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.pipeline_total_progress, overallPercent),
            style = MaterialTheme.typography.bodyMedium,
            color = NofARColors.TextSecondary
        )
        LinearProgressIndicator(
            progress = { overallPercent / 100f },
            modifier = Modifier.fillMaxWidth(),
            color = NofARColors.PrimaryYellow,
            trackColor = NofARColors.SurfaceVariant
        )
        estimatedTimeRemaining?.let { eta ->
            Text(
                text = stringResource(R.string.pipeline_eta, eta),
                style = MaterialTheme.typography.bodySmall,
                color = NofARColors.TextCaption
            )
        }
    }
}

@Composable
private fun PipelineStepRow(step: PipelineStep) {
    val indicator =
        when (step.state) {
            PipelineStepState.COMPLETE -> stringResource(R.string.pipeline_step_complete)
            PipelineStepState.ACTIVE -> stringResource(R.string.pipeline_step_active)
            PipelineStepState.PENDING -> " "
        }
    val indicatorColor =
        when (step.state) {
            PipelineStepState.COMPLETE -> NofARColors.StatusReady
            PipelineStepState.ACTIVE -> NofARColors.PrimaryYellow
            PipelineStepState.PENDING -> NofARColors.TextCaption
        }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (step.state == PipelineStepState.ACTIVE) NofARColors.Surface else NofARColors.Background
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = indicator,
                    modifier = Modifier.size(24.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = indicatorColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NofARColors.TextPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
            step.detailLines.forEach { line ->
                Text(
                    text = line,
                    modifier = Modifier.padding(start = 28.dp, top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = NofARColors.TextSecondary
                )
            }
            step.progress?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress },
                    modifier =
                    Modifier
                        .padding(start = 28.dp, top = 8.dp)
                        .fillMaxWidth(),
                    color = NofARColors.PrimaryYellow,
                    trackColor = NofARColors.SurfaceVariant
                )
            }
        }
    }
}

@Composable
fun NofAREstimatePanel(
    osmEstimateBytes: Long,
    demEstimateBytes: Long,
    totalEstimateBytes: Long,
    demTileCount: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = NofARColors.Surface
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.pipeline_estimated_size),
                style = MaterialTheme.typography.labelMedium,
                color = NofARColors.TextCaption
            )
            Spacer(modifier = Modifier.height(4.dp))
            val osmSizeLabel = NofARFormatters.formatMegabytes(context, osmEstimateBytes)
            Text(
                text = stringResource(R.string.pipeline_osm_data, osmSizeLabel),
                style = MaterialTheme.typography.bodySmall,
                color = NofARColors.TextSecondary
            )
            Text(
                text =
                stringResource(
                    R.string.pipeline_dem_tiles,
                    demTileCount,
                    NofARFormatters.formatMegabytes(context, demEstimateBytes)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = NofARColors.TextSecondary
            )
            val totalSizeLabel = NofARFormatters.formatMegabytes(context, totalEstimateBytes)
            Text(
                text = stringResource(R.string.pipeline_total, totalSizeLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = NofARColors.PrimaryYellow,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
