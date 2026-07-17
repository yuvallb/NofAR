@file:Suppress("TooManyFunctions")

package com.nofar.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nofar.core.data.preferences.UserPreferencesRepository
import com.nofar.core.data.repository.RegionRepository
import com.nofar.core.data.repository.StorageRepository
import com.nofar.core.data.usecase.EvictUnusedDemTilesUseCase
import com.nofar.core.data.usecase.ForceLruEvictionUseCase
import com.nofar.core.data.usecase.LruEvictionUseCase
import com.nofar.core.model.AppConfig
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.LabelLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val demCacheBytes: Long = 0L,
    val entityDbSizeBytes: Long = 0L,
    val entityRowCount: Int = 0,
    val regionCount: Int = 0,
    val evictionThresholdMb: Float = AppConfig.DEM_CACHE_DEFAULT_LIMIT_BYTES / (1024f * 1024f),
    val wifiOnlyDownloads: Boolean = false,
    val simpleModeEnabled: Boolean = false,
    val preferredLabelLanguage: LabelLanguage = LabelLanguage.DEFAULT,
    val showRawSensorOverlay: Boolean = false,
    val keepRawGeoTiff: Boolean = false,
    val prepareDownloadActive: Boolean = false,
    val showPurgeConfirm: Boolean = false,
    val showForceEvictConfirm: Boolean = false,
    val snackbarMessage: String? = null
)

@HiltViewModel
class SettingsViewModel
@Inject
constructor(
    private val storageRepository: StorageRepository,
    private val regionRepository: RegionRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val evictUnusedDemTilesUseCase: EvictUnusedDemTilesUseCase,
    private val lruEvictionUseCase: LruEvictionUseCase,
    private val forceLruEvictionUseCase: ForceLruEvictionUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observePreferences()
        observeActiveDownloads()
        refreshStats()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            combine(
                userPreferencesRepository.wifiOnlyDownloads,
                userPreferencesRepository.simpleModeEnabled,
                userPreferencesRepository.demCacheLimitBytes,
                userPreferencesRepository.showRawSensorOverlay,
                userPreferencesRepository.keepRawGeoTiff
            ) { wifiOnly, simpleMode, cacheLimitBytes, showRaw, keepTif ->
                PreferenceSnapshot(
                    wifiOnly = wifiOnly,
                    simpleMode = simpleMode,
                    cacheLimitMb = cacheLimitBytes / (1024f * 1024f),
                    showRaw = showRaw,
                    keepTif = keepTif
                )
            }.collect { snapshot ->
                _uiState.update {
                    it.copy(
                        wifiOnlyDownloads = snapshot.wifiOnly,
                        simpleModeEnabled = snapshot.simpleMode,
                        evictionThresholdMb = snapshot.cacheLimitMb,
                        showRawSensorOverlay = snapshot.showRaw,
                        keepRawGeoTiff = snapshot.keepTif
                    )
                }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.preferredLabelLanguage.collect { language ->
                _uiState.update { it.copy(preferredLabelLanguage = language) }
            }
        }
    }

    private fun observeActiveDownloads() {
        viewModelScope.launch {
            regionRepository.observeAllRegions().collect { regions ->
                val active = regions.any { it.downloadStatus == DownloadStatus.DOWNLOADING }
                _uiState.update { it.copy(prepareDownloadActive = active) }
            }
        }
    }

    fun refreshStats() {
        viewModelScope.launch {
            val stats = storageRepository.getStorageStats()
            _uiState.update {
                it.copy(
                    demCacheBytes = stats.demCacheSizeBytes,
                    entityDbSizeBytes = stats.entityDbSizeBytes,
                    entityRowCount = stats.entityRowCount,
                    regionCount = stats.regionCount
                )
            }
        }
    }

    fun onWifiOnlyDownloadsChanged(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setWifiOnlyDownloads(enabled)
        }
    }

    fun onSimpleModeChanged(enabled: Boolean, onRootChanged: (Boolean) -> Unit) {
        viewModelScope.launch {
            userPreferencesRepository.setSimpleModeEnabled(enabled)
            onRootChanged(enabled)
        }
    }

    fun onEvictionThresholdChanged(thresholdMb: Float) {
        _uiState.update { it.copy(evictionThresholdMb = thresholdMb) }
        viewModelScope.launch {
            val thresholdBytes = (thresholdMb * 1024 * 1024).toLong()
            userPreferencesRepository.setDemCacheLimitBytes(thresholdBytes)
            lruEvictionUseCase.execute(thresholdBytes)
            refreshStats()
            maybePromptForceEviction(thresholdBytes)
        }
    }

    fun onShowRawSensorOverlayChanged(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setShowRawSensorOverlay(enabled)
        }
    }

    fun onKeepRawGeoTiffChanged(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setKeepRawGeoTiff(enabled)
        }
    }

    fun onPreferredLabelLanguageChanged(language: LabelLanguage) {
        viewModelScope.launch {
            userPreferencesRepository.setPreferredLabelLanguage(language)
        }
    }

    fun showPurgeConfirm() {
        _uiState.update { it.copy(showPurgeConfirm = true) }
    }

    fun dismissPurgeConfirm() {
        _uiState.update { it.copy(showPurgeConfirm = false) }
    }

    fun confirmPurgeUnusedTiles() {
        viewModelScope.launch {
            val thresholdBytes = (_uiState.value.evictionThresholdMb * 1024 * 1024).toLong()
            val result = evictUnusedDemTilesUseCase.execute()
            val message =
                if (result.tilesEvicted > 0) {
                    val freedMb = result.bytesFreed / (1024.0 * 1024.0)
                    "Freed %.1f MB (%d unused tile(s) removed).".format(freedMb, result.tilesEvicted)
                } else {
                    "No unused tiles to remove."
                }
            _uiState.update {
                it.copy(showPurgeConfirm = false, snackbarMessage = message)
            }
            refreshStats()
            maybePromptForceEviction(thresholdBytes)
        }
    }

    fun showForceEvictConfirm() {
        _uiState.update { it.copy(showForceEvictConfirm = true) }
    }

    fun dismissForceEvictConfirm() {
        _uiState.update { it.copy(showForceEvictConfirm = false) }
    }

    fun confirmForceEviction() {
        viewModelScope.launch {
            val thresholdBytes = (_uiState.value.evictionThresholdMb * 1024 * 1024).toLong()
            val result = forceLruEvictionUseCase.execute(thresholdBytes)
            val freedMb = result.bytesFreed / (1024.0 * 1024.0)
            val message =
                if (result.tilesEvicted > 0) {
                    "Removed %d tile(s), freeing %.1f MB. Affected regions marked partial.".format(
                        result.tilesEvicted,
                        freedMb
                    )
                } else {
                    "Cache is already within the limit."
                }
            _uiState.update {
                it.copy(showForceEvictConfirm = false, snackbarMessage = message)
            }
            refreshStats()
        }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private suspend fun maybePromptForceEviction(thresholdBytes: Long) {
        val stats = storageRepository.getStorageStats()
        if (stats.demCacheSizeBytes > thresholdBytes) {
            _uiState.update { it.copy(showForceEvictConfirm = true) }
        }
    }

    private data class PreferenceSnapshot(
        val wifiOnly: Boolean,
        val simpleMode: Boolean,
        val cacheLimitMb: Float,
        val showRaw: Boolean,
        val keepTif: Boolean
    )
}
