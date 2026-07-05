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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
            text = "Downloading: $regionName",
            style = MaterialTheme.typography.titleLarge,
            color = NofARColors.TextPrimary,
            fontWeight = FontWeight.Bold
        )

        steps.forEach { step ->
            PipelineStepRow(step = step)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Total progress: $overallPercent%",
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
                text = "Estimated time remaining: $eta",
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
            PipelineStepState.COMPLETE -> "✓"
            PipelineStepState.ACTIVE -> "▶"
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = NofARColors.Surface
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "ESTIMATED SIZE",
                style = MaterialTheme.typography.labelMedium,
                color = NofARColors.TextCaption
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "OSM Data: ${NofARFormatters.formatMegabytes(osmEstimateBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = NofARColors.TextSecondary
            )
            Text(
                text = "DEM Tiles ($demTileCount): ${NofARFormatters.formatMegabytes(demEstimateBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = NofARColors.TextSecondary
            )
            Text(
                text = "Total: ${NofARFormatters.formatMegabytes(totalEstimateBytes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = NofARColors.PrimaryYellow,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
