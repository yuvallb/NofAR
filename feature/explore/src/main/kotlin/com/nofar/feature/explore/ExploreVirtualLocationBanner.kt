package com.nofar.feature.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nofar.core.designsystem.theme.NofARColors
import java.util.Locale

@Composable
internal fun ExploreVirtualLocationBanner(
    session: VirtualExploreSession?,
    onChangeLocation: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (session == null) return
    Row(
        modifier =
        modifier
            .background(NofARColors.ArOverlayBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "VIRTUAL LOCATION",
                style = MaterialTheme.typography.labelLarge,
                color = NofARColors.PrimaryYellow
            )
            Text(
                text =
                String.format(
                    Locale.US,
                    "%.4f, %.4f",
                    session.observerLat,
                    session.observerLon
                ),
                style = MaterialTheme.typography.bodySmall,
                color = NofARColors.TextPrimary
            )
        }
        TextButton(onClick = onChangeLocation) {
            Text(text = "Change", color = NofARColors.PrimaryYellow)
        }
    }
}
