package com.nofar.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nofar.core.designsystem.theme.NofARColors
import com.nofar.core.designsystem.theme.NofARTheme
import com.nofar.core.model.AppConfig
import kotlin.math.abs
import kotlin.math.roundToInt

private val compassStripMarks =
    listOf(
        0 to "N",
        20 to "20",
        40 to "40",
        60 to "60",
        80 to "80",
        90 to "E",
        100 to "100",
        120 to "120",
        140 to "140",
        160 to "160",
        180 to "S",
        200 to "200",
        220 to "220",
        240 to "240",
        260 to "260",
        270 to "W",
        280 to "280",
        300 to "300",
        320 to "320",
        340 to "340"
    )

private val compassStripViewportHeight = 24.dp
private val compassStripLabelMinWidth = 28.dp
private const val COMPASS_STRIP_LABEL_CULL_MARGIN_DEG = 12f

internal fun compassCenterLabel(bearingDeg: Float): String {
    val rounded = bearingDeg.roundToInt()
    val normalized = ((rounded % 360) + 360) % 360
    return when (normalized) {
        0 -> "N"
        90 -> "E"
        180 -> "S"
        270 -> "W"
        else -> normalized.toString()
    }
}

internal fun compassHeadingDeltaDeg(markDeg: Float, bearingDeg: Float): Float {
    var delta = markDeg - bearingDeg
    while (delta > 180f) delta -= 360f
    while (delta < -180f) delta += 360f
    return delta
}

internal fun nearestUnwrappedMarkDeg(markDeg: Int, bearingDeg: Float): Float {
    val base = markDeg.toFloat()
    val cycle = ((bearingDeg - base) / 360f).roundToInt()
    return base + cycle * 360f
}

internal fun compassMarkCenterOffsetPx(
    markDeg: Float,
    bearingDeg: Float,
    viewportWidthPx: Float,
    horizontalFovDeg: Float
): Float {
    val delta = compassHeadingDeltaDeg(markDeg, bearingDeg)
    return delta * (viewportWidthPx / horizontalFovDeg)
}

@Composable
fun NofARCompassRibbon(bearingDeg: Float, horizontalFovDeg: Float, modifier: Modifier = Modifier) {
    val centerLabel = compassCenterLabel(bearingDeg)
    val labelStyle = MaterialTheme.typography.labelMedium.copy(shadow = arTextShadow)
    val effectiveFovDeg = horizontalFovDeg.coerceAtLeast(AppConfig.CAMERA_HORIZONTAL_FOV_FALLBACK_DEG / 2f)

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
        CompassSlidingStrip(
            bearingDeg = bearingDeg,
            horizontalFovDeg = effectiveFovDeg,
            labelStyle = labelStyle,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        CompassRibbonPointer(
            centerLabel = centerLabel,
            labelStyle = labelStyle,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun CompassSlidingStrip(
    bearingDeg: Float,
    horizontalFovDeg: Float,
    labelStyle: TextStyle,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier =
        modifier
            .fillMaxWidth()
            .height(compassStripViewportHeight)
            .padding(horizontal = 8.dp)
            .clipToBounds()
            .background(NofARColors.ArOverlayBackground.copy(alpha = 0.35f))
    ) {
        val centerPx = constraints.maxWidth / 2f
        val halfFovDeg = horizontalFovDeg / 2f

        compassStripMarks.forEach { (degrees, label) ->
            val markDeg = nearestUnwrappedMarkDeg(degrees, bearingDeg)
            val headingDelta = compassHeadingDeltaDeg(markDeg, bearingDeg)
            if (abs(headingDelta) > halfFovDeg + COMPASS_STRIP_LABEL_CULL_MARGIN_DEG) {
                return@forEach
            }

            val centerX =
                centerPx +
                    compassMarkCenterOffsetPx(
                        markDeg = markDeg,
                        bearingDeg = bearingDeg,
                        viewportWidthPx = constraints.maxWidth.toFloat(),
                        horizontalFovDeg = horizontalFovDeg
                    )
            val labelHalfWidthPx = with(density) { compassStripLabelMinWidth.toPx() / 2f }
            if (centerX < -labelHalfWidthPx || centerX > constraints.maxWidth + labelHalfWidthPx) {
                return@forEach
            }

            Box(
                modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = with(density) { (centerX - labelHalfWidthPx).toDp() })
                    .widthIn(min = compassStripLabelMinWidth),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    text = label,
                    style = labelStyle,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun CompassRibbonPointer(centerLabel: String, labelStyle: TextStyle, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Text(
            text = centerLabel,
            style = labelStyle,
            color = NofARColors.ArAccent,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "▼",
            modifier = Modifier.offset(y = 12.dp),
            color = NofARColors.ArAccent,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

@Preview(name = "Compass North", showBackground = true, backgroundColor = 0xFF3A4A3A)
@Composable
private fun NofARCompassRibbonNorthPreview() {
    NofARTheme {
        NofARCompassRibbon(
            bearingDeg = 0f,
            horizontalFovDeg = AppConfig.CAMERA_HORIZONTAL_FOV_FALLBACK_DEG
        )
    }
}

@Preview(name = "Compass 37°", showBackground = true, backgroundColor = 0xFF3A4A3A)
@Composable
private fun NofARCompassRibbon37Preview() {
    NofARTheme {
        NofARCompassRibbon(
            bearingDeg = 37f,
            horizontalFovDeg = AppConfig.CAMERA_HORIZONTAL_FOV_FALLBACK_DEG
        )
    }
}

@Preview(name = "Compass South", showBackground = true, backgroundColor = 0xFF3A4A3A)
@Composable
private fun NofARCompassRibbonSouthPreview() {
    NofARTheme {
        NofARCompassRibbon(
            bearingDeg = 185f,
            horizontalFovDeg = AppConfig.CAMERA_HORIZONTAL_FOV_FALLBACK_DEG
        )
    }
}
