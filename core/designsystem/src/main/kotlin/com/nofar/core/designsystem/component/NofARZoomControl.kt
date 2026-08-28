package com.nofar.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nofar.core.designsystem.theme.NofARColors

@Composable
fun NofARZoomControl(
    zoomDisplay: String,
    canZoomIn: Boolean,
    canZoomOut: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    zoomInContentDescription: String = "Zoom in",
    zoomOutContentDescription: String = "Zoom out",
    resetContentDescription: String = "Reset zoom"
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ExploreZoomButton(
            onClick = onZoomIn,
            enabled = canZoomIn,
            contentDescription = zoomInContentDescription
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = if (canZoomIn) Color.White else Color.White.copy(alpha = 0.4f)
            )
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = NofARColors.ArOverlayBackground,
            modifier =
            Modifier.semantics { contentDescription = resetContentDescription }
                .clickable(onClick = onReset)
        ) {
            Text(
                text = zoomDisplay,
                modifier =
                Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 32.dp)
                    .widthIn(min = 48.dp)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium.copy(shadow = arTextShadow),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        ExploreZoomButton(
            onClick = onZoomOut,
            enabled = canZoomOut,
            contentDescription = zoomOutContentDescription
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = null,
                tint = if (canZoomOut) Color.White else Color.White.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun ExploreZoomButton(
    onClick: () -> Unit,
    enabled: Boolean,
    contentDescription: String,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = NofARColors.ArOverlayBackground
    ) {
        Box(
            modifier =
            Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}
