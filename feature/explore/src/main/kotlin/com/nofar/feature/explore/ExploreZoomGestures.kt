package com.nofar.feature.explore

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt

fun clampZoom(current: Float, scaleFactor: Float, min: Float, max: Float): Float =
    (current * scaleFactor).coerceIn(min, max)

fun formatZoom(ratio: Float): String {
    if (ratio >= 10f) return "${ratio.roundToInt()}x"
    val formatted = "%.1f".format(ratio)
    return "${formatted.trimEnd('0').trimEnd('.')}x"
}

fun Modifier.exploreZoomGestures(enabled: Boolean, onZoomGesture: (Float) -> Unit): Modifier = if (!enabled) {
    this
} else {
    pointerInput(Unit) {
        detectTransformGestures { _, _, zoom, _ ->
            if (zoom != 1f) {
                onZoomGesture(zoom)
            }
        }
    }
}
