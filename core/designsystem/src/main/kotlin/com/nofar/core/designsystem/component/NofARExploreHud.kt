package com.nofar.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nofar.core.designsystem.theme.NofARColors
import com.nofar.core.model.AltitudeReading

internal val arTextShadow =
    Shadow(
        color = Color.Black,
        offset = Offset(2f, 2f),
        blurRadius = 4f
    )

data class ArLabel(
    val name: String,
    val elevationM: Int?,
    val distanceDisplay: String,
    val isPeak: Boolean,
    val anchorXPx: Float,
    val anchorYPx: Float,
    val hiddenCount: Int = 0,
    val bucketIndex: Int = 0
)

@Composable
fun NofARArLabel(label: ArLabel, modifier: Modifier = Modifier, onHiddenCountClick: ((Int) -> Unit)? = null) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ArLabelTextBlock(label = label, onHiddenCountClick = onHiddenCountClick)
            ArLabelLeaderLine()
            Spacer(modifier = Modifier.height(4.dp))
        }
        ArLabelAnchorDot()
    }
}

@Composable
private fun ArLabelTextBlock(label: ArLabel, onHiddenCountClick: ((Int) -> Unit)? = null) {
    val marker = if (label.isPeak) "▲" else "■"
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = NofARColors.ArOverlayBackground
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                text = "$marker ${label.name}",
                style = MaterialTheme.typography.bodyMedium.copy(shadow = arTextShadow),
                color = if (label.isPeak) NofARColors.ArAccent else Color.White,
                fontWeight = FontWeight.Bold
            )
            label.elevationM?.let { elevation ->
                Text(
                    text = "${elevation}m",
                    style = MaterialTheme.typography.bodySmall.copy(shadow = arTextShadow),
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = label.distanceDisplay,
                style = MaterialTheme.typography.bodySmall.copy(shadow = arTextShadow),
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
            if (label.hiddenCount > 0) {
                ArLabelHiddenCountCapsule(
                    count = label.hiddenCount,
                    onClick = { onHiddenCountClick?.invoke(label.bucketIndex) }
                )
            }
        }
    }
}

@Composable
private fun ArLabelHiddenCountCapsule(count: Int, onClick: () -> Unit) {
    Spacer(modifier = Modifier.height(4.dp))
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = NofARColors.ArOverlayBackground,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = "+ $count more",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = NofARColors.ArAccent
        )
    }
}

@Composable
private fun ArLabelLeaderLine() {
    Box(
        modifier =
        Modifier
            .width(1.dp)
            .height(24.dp)
            .background(Color.White)
    )
}

@Composable
private fun ArLabelAnchorDot() {
    Box(
        modifier =
        Modifier
            .defaultMinSize(minWidth = 4.dp, minHeight = 4.dp)
            .drawBehind {
                drawCircle(color = Color.White, radius = 4.dp.toPx())
            }
    )
}

@Composable
fun NofARExploreAltitudeReadout(altitude: AltitudeReading?, modifier: Modifier = Modifier) {
    Row(
        modifier =
        modifier
            .background(NofARColors.ArOverlayBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val prefix = altitude?.takeIf { it.isEstimate }?.let { "~" }.orEmpty()
        val valueText =
            altitude?.let { reading -> "ALT $prefix${reading.meters} m" } ?: "ALT — m"
        Text(
            text = valueText,
            style = MaterialTheme.typography.titleMedium.copy(shadow = arTextShadow),
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        altitude?.accuracyMeters?.let { accuracy ->
            Text(
                text = " ±${accuracy}m",
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.labelSmall.copy(shadow = arTextShadow),
                color = Color.White
            )
        }
    }
}

/** Vertical space occupied by [NofARExploreBottomHud]; use when stacking controls above it. */
val NofARExploreBottomHudHeight = 48.dp

@Composable
fun NofARExploreBottomHud(
    maxRangeKm: Int,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp)
) {
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .height(NofARExploreBottomHudHeight)
            .background(NofARColors.ArOverlayBackground)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "◎",
            style = MaterialTheme.typography.titleLarge,
            color = NofARColors.ArAccent,
            textAlign = TextAlign.Center
        )
        Text(
            text = "MAX RANGE $maxRangeKm km",
            style = MaterialTheme.typography.labelMedium.copy(shadow = arTextShadow),
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun NofARExploreSideControls(
    modifier: Modifier = Modifier,
    targetIcon: @Composable () -> Unit,
    layersIcon: @Composable () -> Unit,
    filterIcon: @Composable () -> Unit
) {
    Column(
        modifier = modifier.padding(end = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ExploreSideButton(content = targetIcon)
        ExploreSideButton(content = layersIcon)
        ExploreSideButton(content = filterIcon)
    }
}

@Composable
private fun ExploreSideButton(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = NofARColors.ArOverlayBackground
    ) {
        Box(
            modifier =
            Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
fun NofARExploreCameraPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier =
        modifier
            .fillMaxSize()
            .background(Color(0xFF3A4A3A))
    )
}
