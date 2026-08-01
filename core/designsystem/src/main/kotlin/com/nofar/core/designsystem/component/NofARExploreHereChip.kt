package com.nofar.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nofar.core.designsystem.theme.NofARColors

@Composable
fun NofARExploreHereChip(placeName: String?, peakName: String?, peakElevationM: Int?, modifier: Modifier = Modifier) {
    if (placeName == null && peakName == null) return

    Column(
        modifier = modifier.widthIn(max = 320.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        placeName?.let { name ->
            HereContextLine(
                prefix = "HERE",
                marker = "■",
                primaryText = name,
                secondaryText = null
            )
        }
        peakName?.let { name ->
            val elevationSuffix = peakElevationM?.let { elev -> " · ${elev}m" }.orEmpty()
            HereContextLine(
                prefix = "AT",
                marker = "▲",
                primaryText = name,
                secondaryText = elevationSuffix.takeIf { it.isNotEmpty() }
            )
        }
    }
}

@Composable
private fun HereContextLine(prefix: String, marker: String, primaryText: String, secondaryText: String?) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = NofARColors.ArOverlayBackground
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = prefix,
                style = MaterialTheme.typography.labelSmall.copy(shadow = arTextShadow),
                color = NofARColors.YouAreHere,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = marker,
                style = MaterialTheme.typography.labelMedium,
                color = NofARColors.YouAreHere
            )
            Text(
                text = buildString {
                    append(primaryText)
                    secondaryText?.let { append(it) }
                },
                style = MaterialTheme.typography.labelMedium.copy(shadow = arTextShadow),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
