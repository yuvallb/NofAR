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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.nofar.core.designsystem.R as DesignSystemR
import com.nofar.core.designsystem.theme.NofARColors
import com.nofar.core.designsystem.util.NofARFormatters
import com.nofar.core.model.AppConfig
import com.nofar.core.model.AppLanguage
import com.nofar.core.model.AppMetadata
import com.nofar.core.network.OverpassConfig

internal const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"
internal const val APACHE_LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0"

@Composable
internal fun SettingsContent(
    uiState: SettingsUiState,
    onAppLanguageChanged: (AppLanguage) -> Unit,
    onWifiOnlyChanged: (Boolean) -> Unit,
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
        SettingsLanguageSection(
            selectedLanguage = uiState.appLanguage,
            onLanguageChanged = onAppLanguageChanged
        )
        SettingsSectionDivider()
        SettingsGeneralSection(
            wifiOnlyDownloads = uiState.wifiOnlyDownloads,
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
private fun SettingsLanguageSection(selectedLanguage: AppLanguage, onLanguageChanged: (AppLanguage) -> Unit) {
    SettingsSectionTitle(stringResource(R.string.settings_language_section))
    Column(modifier = Modifier.selectableGroup()) {
        LanguageOptionRow(
            label = stringResource(R.string.settings_language_system),
            selected = selectedLanguage == AppLanguage.SYSTEM,
            onSelect = { onLanguageChanged(AppLanguage.SYSTEM) },
            testTag = "language_system"
        )
        LanguageOptionRow(
            label = stringResource(R.string.settings_language_english),
            selected = selectedLanguage == AppLanguage.ENGLISH,
            onSelect = { onLanguageChanged(AppLanguage.ENGLISH) },
            testTag = "language_english"
        )
        LanguageOptionRow(
            label = stringResource(R.string.settings_language_hebrew),
            selected = selectedLanguage == AppLanguage.HEBREW,
            onSelect = { onLanguageChanged(AppLanguage.HEBREW) },
            testTag = "language_hebrew"
        )
    }
}

@Composable
private fun LanguageOptionRow(label: String, selected: Boolean, onSelect: () -> Unit, testTag: String) {
    androidx.compose.foundation.layout.Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = NofARColors.PrimaryYellow)
        )
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = NofARColors.TextPrimary)
    }
}

@Composable
private fun SettingsGeneralSection(wifiOnlyDownloads: Boolean, onWifiOnlyChanged: (Boolean) -> Unit) {
    SettingsSectionTitle(stringResource(R.string.settings_general_section))
    SettingsToggleRow(
        title = stringResource(R.string.settings_wifi_only_title),
        subtitle = stringResource(R.string.settings_wifi_only_subtitle),
        checked = wifiOnlyDownloads,
        onCheckedChange = onWifiOnlyChanged,
        testTag = "wifi_only_toggle"
    )
    Spacer(modifier = Modifier.height(8.dp))
    val cellularMb = AppConfig.CELLULAR_DOWNLOAD_WARNING_BYTES / (1024 * 1024)
    Text(
        text = stringResource(R.string.settings_cellular_threshold, cellularMb),
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
    val context = LocalContext.current
    SettingsSectionTitle(stringResource(R.string.settings_storage_section))
    val demCacheLabel = NofARFormatters.formatMegabytes(context, uiState.demCacheBytes)
    Text(
        text = stringResource(R.string.settings_dem_cache, demCacheLabel),
        style = MaterialTheme.typography.bodyMedium,
        color = NofARColors.TextPrimary
    )
    Text(
        text =
        stringResource(
            R.string.settings_entity_database,
            NofARFormatters.formatMegabytes(context, uiState.entityDbSizeBytes)
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = NofARColors.TextPrimary
    )
    Text(
        text = stringResource(R.string.settings_regions, uiState.regionCount),
        style = MaterialTheme.typography.bodyMedium,
        color = NofARColors.TextPrimary
    )
    val entityLabel = NofARFormatters.formatCount(context, uiState.entityRowCount)
    Text(
        text = stringResource(R.string.settings_entity_rows, entityLabel),
        style = MaterialTheme.typography.bodySmall,
        color = NofARColors.TextSecondary
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.settings_typical_usage),
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
        Text(stringResource(R.string.settings_clear_unused_tiles), color = NofARColors.PrimaryYellow)
    }
    if (uiState.prepareDownloadActive) {
        Text(
            text = stringResource(R.string.settings_storage_disabled),
            style = MaterialTheme.typography.bodySmall,
            color = NofARColors.TextCaption
        )
    }
}

@Composable
private fun SettingsEvictionThreshold(thresholdMb: Float, enabled: Boolean, onThresholdChanged: (Float) -> Unit) {
    Text(
        text = stringResource(R.string.settings_dem_cache_limit),
        style = MaterialTheme.typography.labelMedium,
        color = NofARColors.TextCaption
    )
    Text(
        text = stringResource(R.string.settings_lru_limit, thresholdMb.toInt()),
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
        text = stringResource(R.string.settings_default_limit, defaultMb),
        style = MaterialTheme.typography.bodySmall,
        color = NofARColors.TextCaption
    )
}

@Composable
internal fun SettingsLegalSection() {
    val context = LocalContext.current
    SettingsSectionTitle(stringResource(R.string.settings_legal_section))
    SettingsLinkText(
        text = stringResource(R.string.settings_osm_attribution),
        testTag = "osm_attribution",
        onClick = { openHttpsUrl(context, OSM_COPYRIGHT_URL) }
    )
    Text(
        text = stringResource(R.string.settings_copernicus_attribution),
        modifier = Modifier.testTag("copernicus_attribution"),
        style = MaterialTheme.typography.bodySmall,
        color = NofARColors.TextSecondary
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.settings_overpass_note),
        style = MaterialTheme.typography.bodySmall,
        color = NofARColors.TextSecondary
    )
    OverpassConfig.mirrorBaseUrls.forEach { mirror ->
        Text(
            text = stringResource(R.string.settings_mirror_bullet, mirror),
            style = MaterialTheme.typography.bodySmall,
            color = NofARColors.TextCaption
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.settings_odbl_notice),
        style = MaterialTheme.typography.bodySmall,
        color = NofARColors.TextCaption
    )
}

@Composable
internal fun SettingsAboutSection() {
    val context = LocalContext.current
    SettingsSectionTitle(stringResource(R.string.settings_about_section))
    Text(
        text = stringResource(DesignSystemR.string.app_brand),
        style = MaterialTheme.typography.titleMedium,
        color = NofARColors.TextPrimary
    )
    Text(
        text = stringResource(R.string.settings_tagline),
        style = MaterialTheme.typography.bodyMedium,
        color = NofARColors.TextSecondary
    )
    Text(
        text = stringResource(R.string.settings_version, AppMetadata.VERSION_NAME, AppMetadata.VERSION_CODE),
        modifier = Modifier.testTag("app_version"),
        style = MaterialTheme.typography.bodySmall,
        color = NofARColors.TextSecondary
    )
    Spacer(modifier = Modifier.height(8.dp))
    SettingsLinkText(
        text = stringResource(R.string.settings_github),
        onClick = { openHttpsUrl(context, AppMetadata.GITHUB_REPOSITORY_URL) }
    )
    SettingsLinkText(
        text = stringResource(R.string.settings_apache_license),
        testTag = "apache_license",
        onClick = { openHttpsUrl(context, APACHE_LICENSE_URL) }
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.settings_privacy_note),
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
    SettingsSectionTitle(stringResource(R.string.settings_debug_section))
    SettingsToggleRow(
        title = stringResource(R.string.settings_raw_sensor_title),
        subtitle = stringResource(R.string.settings_raw_sensor_subtitle),
        checked = showRawSensorOverlay,
        onCheckedChange = onShowRawSensorChanged
    )
    Spacer(modifier = Modifier.height(8.dp))
    SettingsToggleRow(
        title = stringResource(R.string.settings_keep_geotiff_title),
        subtitle = stringResource(R.string.settings_keep_geotiff_subtitle),
        checked = keepRawGeoTiff,
        onCheckedChange = onKeepRawGeoTiffChanged
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.settings_resolution_level, AppConfig.defaultResolutionLevel.name),
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
        verticalAlignment = Alignment.CenterVertically
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
