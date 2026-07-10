package com.nofar.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nofar.core.designsystem.R
import com.nofar.core.designsystem.theme.NofARColors
import com.nofar.core.designsystem.util.NofARFormatters
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.Region
import java.util.UUID

@Composable
fun NofARRegionCard(
    state: RegionCardState,
    onPrepare: (UUID) -> Unit,
    onDelete: (UUID) -> Unit,
    modifier: Modifier = Modifier,
    deleteIcon: @Composable () -> Unit
) {
    val region = state.region
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = NofARColors.Surface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            RegionCardHeader(region = region)
            if (state.isYouAreHere) {
                Spacer(modifier = Modifier.height(8.dp))
                NofARYouAreHereBadge()
            }
            RegionCardMetadata(state = state)
            RegionCardDownloadProgress(region = region)
            RegionCardActions(
                state = state,
                onPrepare = onPrepare,
                onDelete = onDelete,
                deleteIcon = deleteIcon
            )
        }
    }
}

@Composable
private fun RegionCardHeader(region: Region) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = region.name,
            style = MaterialTheme.typography.titleMedium,
            color = NofARColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        NofARStatusBadge(status = region.downloadStatus, progressPct = region.downloadProgressPct)
    }
}

@Composable
private fun RegionCardMetadata(state: RegionCardState) {
    val region = state.region
    Spacer(modifier = Modifier.height(8.dp))
    val center =
        "${NofARFormatters.formatCoordinate(region.centerLat)}, " +
            NofARFormatters.formatCoordinate(region.centerLon)
    Text(
        text =
        stringResource(
            R.string.region_card_center,
            center,
            NofARFormatters.formatRadiusKm(region.radiusM)
        ),
        style = MaterialTheme.typography.bodySmall,
        color = NofARColors.TextSecondary
    )
    val sizeText =
        if (state.demSizeBytes > 0L) {
            stringResource(
                R.string.region_card_size_breakdown,
                NofARFormatters.formatMegabytes(state.osmSizeBytes),
                NofARFormatters.formatMegabytes(state.demSizeBytes)
            )
        } else {
            stringResource(
                R.string.region_card_size_estimate,
                NofARFormatters.formatMegabytes(region.estimatedSizeBytes)
            )
        }
    Text(
        text =
        stringResource(
            R.string.region_card_entities,
            NofARFormatters.formatCount(region.entityCount),
            sizeText
        ),
        style = MaterialTheme.typography.bodySmall,
        color = NofARColors.TextSecondary
    )
    Text(
        text =
        stringResource(
            R.string.region_card_timestamps,
            NofARFormatters.formatTimestamp(region.osmDatasetVersion),
            NofARFormatters.formatTimestamp(state.latestDemTimestamp)
        ),
        style = MaterialTheme.typography.bodySmall,
        color = NofARColors.TextSecondary
    )
}

@Composable
private fun RegionCardDownloadProgress(region: Region) {
    if (region.downloadStatus != DownloadStatus.DOWNLOADING) return
    Spacer(modifier = Modifier.height(8.dp))
    LinearProgressIndicator(
        progress = { region.downloadProgressPct / 100f },
        modifier = Modifier.fillMaxWidth(),
        color = NofARColors.StatusDownloading,
        trackColor = NofARColors.SurfaceVariant
    )
}

@Composable
private fun RegionCardActions(
    state: RegionCardState,
    onPrepare: (UUID) -> Unit,
    onDelete: (UUID) -> Unit,
    deleteIcon: @Composable () -> Unit
) {
    val region = state.region
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            RegionCardPrimaryAction(
                region = region,
                onPrepare = onPrepare,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = { onDelete(region.id) },
            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        ) {
            deleteIcon()
        }
    }
}

@Composable
private fun RegionCardPrimaryAction(region: Region, onPrepare: (UUID) -> Unit, modifier: Modifier = Modifier) {
    when (region.downloadStatus) {
        DownloadStatus.NOT_DOWNLOADED ->
            NofARPrimaryButton(
                text = stringResource(R.string.region_card_prepare),
                onClick = { onPrepare(region.id) },
                modifier = modifier
            )
        DownloadStatus.DOWNLOADING ->
            NofARSecondaryOutlinedButton(
                text = stringResource(R.string.region_card_view_progress),
                onClick = { onPrepare(region.id) },
                modifier = modifier
            )
        else ->
            NofARPrimaryButton(
                text = stringResource(R.string.region_card_update_data),
                onClick = { onPrepare(region.id) },
                modifier = modifier
            )
    }
}

@Composable
fun NofARStorageSummary(
    demCacheBytes: Long,
    entitiesDbBytes: Long,
    freeSpaceBytes: Long,
    onAdvancedStorageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .background(NofARColors.SurfaceVariant)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.storage_summary_title),
            style = MaterialTheme.typography.labelMedium,
            color = NofARColors.TextCaption
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.storage_dem_cache, NofARFormatters.formatMegabytes(demCacheBytes)),
            style = MaterialTheme.typography.bodySmall,
            color = NofARColors.TextSecondary
        )
        Text(
            text = stringResource(R.string.storage_entities_db, NofARFormatters.formatMegabytes(entitiesDbBytes)),
            style = MaterialTheme.typography.bodySmall,
            color = NofARColors.TextSecondary
        )
        Text(
            text = stringResource(R.string.storage_free_space, NofARFormatters.formatMegabytes(freeSpaceBytes)),
            style = MaterialTheme.typography.bodySmall,
            color = NofARColors.TextSecondary
        )
        Spacer(modifier = Modifier.height(12.dp))
        NofARSecondaryOutlinedButton(
            text = stringResource(R.string.storage_advanced),
            onClick = onAdvancedStorageClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun NofARSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = NofARColors.TextCaption
    )
}

@Composable
fun NofARRegionListDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(vertical = 8.dp),
        color = NofARColors.SurfaceVariant
    )
}
