package com.nofar.core.designsystem.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Place
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Placeholder icon set — swap for custom vectors in a later polish pass.
 */
object NofARIcons {
    val Place: ImageVector = Icons.Default.Place
    val Peak: ImageVector = Icons.Default.Landscape
    val Compass: ImageVector = Icons.Default.Explore
    val Download: ImageVector = Icons.Default.Download
    val Delete: ImageVector = Icons.Default.Delete

    /** Ridge outline used as the peak marker on AR labels; stays legible at inline text size. */
    val PeakMarker: ImageVector = buildPeakMarker()

    /** Filled house with a door cutout, used as the place marker on AR labels. */
    val PlaceMarker: ImageVector = buildPlaceMarker()
}

private const val MARKER_VIEWPORT = 24f
private const val MARKER_STROKE_WIDTH = 2f

private fun buildPeakMarker(): ImageVector = ImageVector.Builder(
    name = "PeakMarker",
    defaultWidth = MARKER_VIEWPORT.dp,
    defaultHeight = MARKER_VIEWPORT.dp,
    viewportWidth = MARKER_VIEWPORT,
    viewportHeight = MARKER_VIEWPORT
).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = MARKER_STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(3.5f, 15f)
        lineTo(10f, 6.5f)
        lineTo(14f, 11.5f)
        lineTo(16.5f, 8.5f)
        lineTo(20.5f, 15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = MARKER_STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round
    ) {
        moveTo(2.5f, 19f)
        lineTo(21.5f, 19f)
    }
}.build()

private fun buildPlaceMarker(): ImageVector = ImageVector.Builder(
    name = "PlaceMarker",
    defaultWidth = MARKER_VIEWPORT.dp,
    defaultHeight = MARKER_VIEWPORT.dp,
    viewportWidth = MARKER_VIEWPORT,
    viewportHeight = MARKER_VIEWPORT
).apply {
    path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
        moveTo(12f, 3.5f)
        lineTo(21f, 11.5f)
        lineTo(19f, 11.5f)
        lineTo(19f, 20f)
        lineTo(5f, 20f)
        lineTo(5f, 11.5f)
        lineTo(3f, 11.5f)
        close()
        moveTo(10f, 20f)
        lineTo(10f, 14.5f)
        lineTo(14f, 14.5f)
        lineTo(14f, 20f)
        close()
    }
}.build()
