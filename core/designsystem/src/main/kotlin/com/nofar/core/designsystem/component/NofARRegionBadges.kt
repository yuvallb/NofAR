package com.nofar.core.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nofar.core.designsystem.R
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

@Composable
private fun statusBadgeColors(status: DownloadStatus, progressPct: Int): Triple<String, Color, Color> = when (status) {
    DownloadStatus.READY ->
        Triple(stringResource(R.string.badge_ready), NofARColors.StatusReady, Color.White)
    DownloadStatus.PARTIAL ->
        Triple(stringResource(R.string.badge_partial), NofARColors.StatusPartial, Color.Black)
    DownloadStatus.DOWNLOADING ->
        Triple(
            stringResource(R.string.badge_downloading, progressPct),
            NofARColors.StatusDownloading,
            Color.White
        )
    DownloadStatus.NOT_DOWNLOADED ->
        Triple(
            stringResource(R.string.badge_not_downloaded),
            NofARColors.StatusNotDownloaded,
            Color.White
        )
}

@Composable
fun NofARYouAreHereBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = NofARColors.YouAreHere
    ) {
        Text(
            text = stringResource(R.string.badge_you_are_here),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = NofARColors.OnPrimaryYellow
        )
    }
}
