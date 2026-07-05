package com.nofar.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nofar.core.designsystem.theme.NofARColors

private val arTextShadow =
    Shadow(
        color = Color.Black,
        offset = Offset(2f, 2f),
        blurRadius = 4f
    )

data class ArLabel(
    val name: String,
    val elevationM: Int?,
    val distanceKm: String,
    val isPeak: Boolean,
    val xFraction: Float,
    val yFraction: Float,
    val hiddenCount: Int = 0
)

@Composable
fun NofARCompassRibbon(headings: List<String>, centerHeading: String, modifier: Modifier = Modifier) {
    Box(
        modifier =
        modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(NofARColors.ArOverlayBackground, Color.Transparent)
                )
            )
            .padding(top = 8.dp, bottom = 16.dp)
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            headings.forEach { heading ->
                Text(
                    text = heading,
                    style = MaterialTheme.typography.labelMedium.copy(shadow = arTextShadow),
                    color = if (heading == centerHeading) NofARColors.ArAccent else Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            text = "▼",
            modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 12.dp),
            color = NofARColors.ArAccent,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

@Composable
fun NofARArLabel(label: ArLabel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ArLabelTextBlock(label = label)
        ArLabelLeaderLine()
        ArLabelAnchorDot()
    }
}

@Composable
private fun ArLabelTextBlock(label: ArLabel) {
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
                text = "${label.distanceKm} km",
                style = MaterialTheme.typography.bodySmall.copy(shadow = arTextShadow),
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
            if (label.hiddenCount > 0) {
                ArLabelHiddenCountCapsule(count = label.hiddenCount)
            }
        }
    }
}

@Composable
private fun ArLabelHiddenCountCapsule(count: Int) {
    Spacer(modifier = Modifier.height(4.dp))
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = NofARColors.ArOverlayBackground
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
fun NofARExploreBottomHud(altitudeM: String, maxRangeKm: Int, modifier: Modifier = Modifier) {
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .background(NofARColors.ArOverlayBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "ALT $altitudeM m",
            style = MaterialTheme.typography.labelMedium.copy(shadow = arTextShadow),
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
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
