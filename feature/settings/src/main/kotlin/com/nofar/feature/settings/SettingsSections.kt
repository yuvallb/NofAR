@file:Suppress("TooManyFunctions")

package com.nofar.feature.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.nofar.core.designsystem.theme.NofARColors
import com.nofar.core.designsystem.util.NofARFormatters
import com.nofar.core.model.AppConfig
import com.nofar.core.model.AppMetadata
import com.nofar.core.network.OverpassConfig

internal const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"
internal const val APACHE_LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0"

@Composable
internal fun SettingsContent(
    uiState: SettingsUiState,
    onWifiOnlyChanged: (Boolean) -> Unit,
    onSimpleModeChanged: (Boolean) -> Unit,
    onEvictionThresholdChanged: (Float) -> Unit,
    onShowPurgeConfirm: () -> Unit,
    onShowRawSensorChanged: (Boolean) -> Unit,
    onKeepRawGeoTiffChanged: (Boolean) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        SettingsGeneralSection(
            simpleModeEnabled = uiState.simpleModeEnabled,
            wifiOnlyDownloads = uiState.wifiOnlyDownloads,
            onSimpleModeChanged = onSimpleModeChanged,
            onWifiOnlyChanged = onWifiOnlyChanged
        )
        SettingsSectionDivider()
        SettingsStorageSection(
            uiState = uiState,
            onEvictionThresholdChanged = onEvictionThresholdChanged,
            onShowPurgeConfirm = onShowPurgeConfirm
        )
        SettingsSectionDivider()
        SettingsLegalSection()
        SettingsSectionDivider()
        SettingsAboutSection()
        if (BuildConfig.DEBUG) {
            SettingsSectionDivider()
            SettingsDebugSection(
                showRawSensorOverlay = uiState.showRawSensorOverlay,
                keepRawGeoTiff = uiState.keepRawGeoTiff,
                onShowRawSensorChanged = onShowRawSensorChanged,
                onKeepRawGeoTiffChanged = onKeepRawGeoTiffChanged
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
internal fun SettingsSectionDivider() {
    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = NofARColors.SurfaceVariant)
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
internal fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = NofARColors.TextCaption
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun SettingsGeneralSection(
    simpleModeEnabled: Boolean,
    wifiOnlyDownloads: Boolean,
    onSimpleModeChanged: (Boolean) -> Unit,
    onWifiOnlyChanged: (Boolean) -> Unit
) {
    SettingsSectionTitle("GENERAL")
    SettingsToggleRow(
        title = stringResource(com.nofar.core.ui.R.string.settings_simple_mode_title),
        subtitle = stringResource(com.nofar.core.ui.R.string.settings_simple_mode_subtitle),
        checked = simpleModeEnabled,
        onCheckedChange = onSimpleModeChanged,
        testTag = "simple_mode_toggle"
    )
    Spacer(modifier = Modifier.height(8.dp))
    SettingsToggleRow(
        title = "Wi-Fi only downloads",
        subtitle = stringResource(com.nofar.core.ui.R.string.settings_wifi_only_subtitle),
        checked = wifiOnlyDownloads,
        onCheckedChange = onWifiOnlyChanged,
        testTag = "wifi_only_toggle"
    )
    Spacer(modifier = Modifier.height(8.dp))
    val cellularMb = AppConfig.CELLULAR_DOWNLOAD_WARNING_BYTES / (1024 * 1024)
    Text(
        text = "Cellular warning threshold: $cellularMb MB (read-only)",
        style = MaterialTheme.typography.bodySmall,
        color = NofARColors.TextSecondary
    )
}

@Composable
private fun SettingsStorageSection(
    uiState: SettingsUiState,
    onEvictionThresholdChanged: (Float) -> Unit,
    onShowPurgeConfirm: () -> Unit
) {
    SettingsSectionTitle("STORAGE")
    Text(
        text = "DEM cache: ${NofARFormatters.formatMegabytes(uiState.demCacheBytes)}",
        style = MaterialTheme.typography.bodyMedium,
        color = NofARColors.TextPrimary
    )
    Text(
        text = "Entity database: ${NofARFormatters.formatMegabytes(uiState.entityDbSizeBytes)}",
        style = MaterialTheme.typography.bodyMedium,
        color = NofARColors.TextPrimary
    )
    Text(
        text = "Regions: ${uiState.regionCount}",
        style = MaterialTheme.typography.bodyMedium,
        color = NofARColors.TextPrimary
    )
    val entityLabel = NofARFormatters.formatCount(uiState.entityRowCount)
    Text(
        text = "Entity rows: $entityLabel",
        style = MaterialTheme.typography.bodySmall,
        color = NofARColors.TextSecondary
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Typical 3–5 regions: ~150–500 MB",
        style = MaterialTheme.typography.bodySmall,
        color = NofARColors.TextCaption
    )
    Spacer(modifier = Modifier.height(16.dp))
    SettingsEvictionThreshold(
        thresholdMb = uiState.evictionThresholdMb,
        enabled = !uiState.prepareDownloadActive,
        onThresholdChanged = onEvictionThresholdChanged
    )
    Spacer(modifier = Modifier.height(16.dp))
    TextButton(
        onClick = onShowPurgeConfirm,
        enabled = !uiState.prepareDownloadActive,
        modifier = Modifier.testTag("clear_unused_tiles_button")
    ) {
        Text("CLEAR UNUSED DEM TILES", color = NofARColors.PrimaryYellow)
    }
    if (uiState.prepareDownloadActive) {
        Text(
            text = "Storage actions are disabled while a download is in progress.",
            style = MaterialTheme.typography.bodySmall,
            color = NofARColors.TextCaption
        )
    }
}

@Composable
private fun SettingsEvictionThreshold(thresholdMb: Float, enabled: Boolean, onThresholdChanged: (Float) -> Unit) {
    Text(
        text = "DEM cache limit",
        style = MaterialTheme.typography.labelMedium,
        color = NofARColors.TextCaption
    )
    Text(
        text = "LRU limit: ${thresholdMb.toInt()} MB",
        style = MaterialTheme.typography.bodySmall,
        color = NofARColors.TextSecondary
    )
    Slider(
        value = thresholdMb,
        onValueChange = onThresholdChanged,
        enabled = enabled,
        valueRange = 100f..2_048f,
        steps = 19,
        colors =
        SliderDefaults.colors(
            thumbColor = NofARColors.PrimaryYellow,
            activeTrackColor = NofARColors.PrimaryYellow,
            inactiveTrackColor = NofARColors.SurfaceVariant
        )
    )
    val defaultMb = AppConfig.DEM_CACHE_DEFAULT_LIMIT_BYTES / (1024 * 1024)
    Text(
        text = "Default: $defaultMb MB",
        style = MaterialTheme.typography.bodySmall,
        color = NofARColors.TextCaption
    )
}

@Composable
internal fun SettingsLegalSection() {
    val context = LocalContext.current
    SettingsSectionTitle("LEGAL")
    SettingsLinkText(
        text = "© OpenStreetMap contributors",
        testTag = "osm_attribution",
        onClick = { openHttpsUrl(context, OSM_COPYRIGHT_URL) }
    )
    Text(
        text = "Copernicus DEM, ESA / Airbus",
        modifier = Modifier.testTag("copernicus_attribution"),
        style = MaterialTheme.typography.bodySmall,
        color = NofARColors.TextSecondary
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Map data retrieved via the public Overpass API during Prepare mode.",
        style = MaterialTheme.typography.bodySmall,
        color = NofARColors.TextSecondary
    )
    OverpassConfig.mirrorBaseUrls.forEach { mirror ->
        Text(
            text = "• $mirror",
            style = MaterialTheme.typography.bodySmall,
            color = NofARColors.TextCaption
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text =
        "OpenStreetMap data is available under the Open Database License (ODbL). " +
            "You are free to copy, distribute, transmit, and adapt OSM data as long as " +
            "you credit OpenStreetMap and its contributors.",
        style = MaterialTheme.typography.bodySmall,
        color = NofARColors.TextCaption
    )
}

@Composable
internal fun SettingsAboutSection() {
    val context = LocalContext.current
    SettingsSectionTitle("ABOUT")
    Text(
        text = "NofAR",
        style = MaterialTheme.typography.titleMedium,
        color = NofARColors.TextPrimary
    )
    Text(
        text = "point, explore, discover",
        style = MaterialTheme.typography.bodyMedium,
        color = NofARColors.TextSecondary
    )
    Text(
        text = "Version ${AppMetadata.VERSION_NAME} (${AppMetadata.VERSION_CODE})",
        modifier = Modifier.testTag("app_version"),
        style = MaterialTheme.typography.bodySmall,
        color = NofARColors.TextSecondary
    )
    Spacer(modifier = Modifier.height(8.dp))
    SettingsLinkText(
        text = "Source code on GitHub",
        onClick = { openHttpsUrl(context, AppMetadata.GITHUB_REPOSITORY_URL) }
    )
    SettingsLinkText(
        text = "Licensed under Apache License 2.0",
        testTag = "apache_license",
        onClick = { openHttpsUrl(context, APACHE_LICENSE_URL) }
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "No accounts, no analytics, no data leaves your device except during map downloads.",
        style = MaterialTheme.typography.bodySmall,
        color = NofARColors.TextCaption
    )
}

@Composable
private fun SettingsDebugSection(
    showRawSensorOverlay: Boolean,
    keepRawGeoTiff: Boolean,
    onShowRawSensorChanged: (Boolean) -> Unit,
    onKeepRawGeoTiffChanged: (Boolean) -> Unit
) {
    SettingsSectionTitle("DEBUG")
    SettingsToggleRow(
        title = "Use raw sensor overlay",
        subtitle = "Project AR labels with unsmoothed compass values",
        checked = showRawSensorOverlay,
        onCheckedChange = onShowRawSensorChanged
    )
    Spacer(modifier = Modifier.height(8.dp))
    SettingsToggleRow(
        title = "Keep raw GeoTIFF after conversion",
        subtitle = "Retain source .tif files in dem/raw (uses more storage)",
        checked = keepRawGeoTiff,
        onCheckedChange = onKeepRawGeoTiffChanged
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Resolution level: ${AppConfig.defaultResolutionLevel.name} (read-only)",
        style = MaterialTheme.typography.bodySmall,
        color = NofARColors.TextSecondary
    )
}

@Composable
internal fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String? = null
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = NofARColors.TextPrimary)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = NofARColors.TextCaption)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier,
            colors =
            SwitchDefaults.colors(
                checkedThumbColor = NofARColors.OnPrimaryYellow,
                checkedTrackColor = NofARColors.PrimaryYellow,
                uncheckedThumbColor = NofARColors.TextSecondary,
                uncheckedTrackColor = NofARColors.SurfaceVariant
            )
        )
    }
}

@Composable
internal fun SettingsLinkText(text: String, onClick: () -> Unit, testTag: String? = null) {
    Text(
        text = text,
        modifier =
        Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        style = MaterialTheme.typography.bodySmall,
        color = NofARColors.PrimaryYellow
    )
}

internal fun openHttpsUrl(context: android.content.Context, url: String) {
    val uri = url.toUri()
    if (uri.scheme != "https") return
    val intent = Intent(Intent.ACTION_VIEW, uri)
    context.startActivity(intent)
}
