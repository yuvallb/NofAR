package com.nofar.core.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nofar.core.designsystem.theme.NofARColors
import com.nofar.core.model.DownloadStatus

@Composable
fun NofARStatusBadge(status: DownloadStatus, progressPct: Int = 0, modifier: Modifier = Modifier) {
    val (label, background, foreground) = statusBadgeColors(status, progressPct)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = background
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = foreground
        )
    }
}

private fun statusBadgeColors(status: DownloadStatus, progressPct: Int): Triple<String, Color, Color> = when (status) {
    DownloadStatus.READY -> Triple("READY", NofARColors.StatusReady, Color.White)
    DownloadStatus.PARTIAL -> Triple("PARTIAL", NofARColors.StatusPartial, Color.Black)
    DownloadStatus.DOWNLOADING -> {
        val label = "DOWNLOADING $progressPct%"
        Triple(label, NofARColors.StatusDownloading, Color.White)
    }
    DownloadStatus.NOT_DOWNLOADED -> Triple("NOT DOWNLOADED", NofARColors.StatusNotDownloaded, Color.White)
}

@Composable
fun NofARYouAreHereBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = NofARColors.YouAreHere
    ) {
        Text(
            text = "YOU ARE HERE",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = NofARColors.OnPrimaryYellow
        )
    }
}
