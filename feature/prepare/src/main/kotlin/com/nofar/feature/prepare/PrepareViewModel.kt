@file:Suppress("TooManyFunctions")

package com.nofar.feature.prepare

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nofar.core.data.preferences.DownloadPreferences
import com.nofar.core.data.prepare.PrepareDownloadOrchestrator
import com.nofar.core.data.prepare.PrepareDownloadScheduler
import com.nofar.core.data.prepare.PrepareEstimator
import com.nofar.core.data.prepare.PreparePhase
import com.nofar.core.data.prepare.PrepareProgress
import com.nofar.core.data.prepare.PrepareWorkState
import com.nofar.core.data.repository.RegionRepository
import com.nofar.core.model.AppConfig
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.Region
import com.nofar.core.model.RegionBounds
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PrepareDownloadUiState {
    IDLE,
    ESTIMATING,
    DOWNLOADING,
    PAUSED,
    COMPLETE,
    ERROR
}

data class PrepareUiState(
    val step: PrepareStep = PrepareStep.DEFINE,
    val downloadUiState: PrepareDownloadUiState = PrepareDownloadUiState.IDLE,
    val regionName: String = "",
    val centerLat: Double = 32.0,
    val centerLon: Double = 35.0,
    val radiusKm: Double = 10.0,
    val regionId: UUID? = null,
    val existingRegion: Region? = null,
    val estimateBytes: Long = 0L,
    val demTileCount: Int = 0,
    val progress: PrepareProgress? = null,
    val errorMessage: String? = null,
    val showCellularWarning: Boolean = false,
    val nameError: String? = null
)

enum class PrepareStep {
    DEFINE,
    ESTIMATE,
    DOWNLOAD,
    COMPLETE
}

@HiltViewModel
class PrepareViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val regionRepository: RegionRepository,
    private val downloadScheduler: PrepareDownloadScheduler,
    private val orchestrator: PrepareDownloadOrchestrator,
    private val downloadPreferences: DownloadPreferences,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(PrepareUiState())
    val uiState: StateFlow<PrepareUiState> = _uiState.asStateFlow()

    init {
        savedStateHandle.get<String>("regionId")?.takeIf { it.isNotBlank() }?.let { id ->
            loadExistingRegion(UUID.fromString(id))
        }
        refreshEstimate()
        viewModelScope.launch {
            orchestrator.progress.collect { progress ->
                if (progress != null) {
                    _uiState.update { state ->
                        state.copy(
                            progress = progress,
                            downloadUiState = PrepareDownloadUiState.DOWNLOADING,
                            step = PrepareStep.DOWNLOAD
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(500)
                pollDownloadProgressFromDb()
            }
        }
    }

    private suspend fun pollDownloadProgressFromDb() {
        val regionId = _uiState.value.regionId
        val region = regionId?.let { regionRepository.getRegion(it) }
        if (region?.downloadStatus == DownloadStatus.DOWNLOADING) {
            _uiState.update { state ->
                state.copy(
                    progress =
                    state.progress?.copy(overallPercent = region.downloadProgressPct)
                        ?: PrepareProgress(
                            phase = PreparePhase.OSM,
                            overallPercent = region.downloadProgressPct
                        ),
                    downloadUiState = PrepareDownloadUiState.DOWNLOADING,
                    step = PrepareStep.DOWNLOAD
                )
            }
        }
    }

    fun onMapTap(lat: Double, lon: Double) {
        val suggestedName =
            "Region near ${"%.2f".format(lat)}, ${"%.2f".format(lon)}"
        _uiState.update {
            val name =
                if (it.regionName.isBlank() || it.regionName.startsWith("Region near")) {
                    suggestedName
                } else {
                    it.regionName
                }
            it.copy(
                centerLat = lat,
                centerLon = lon,
                regionName = name,
                step = PrepareStep.DEFINE,
                errorMessage = null
            )
        }
        refreshEstimate()
    }

    fun onRadiusChanged(radiusKm: Double) {
        val clamped =
            radiusKm.coerceIn(AppConfig.REGION_RADIUS_MIN_KM, AppConfig.REGION_RADIUS_MAX_KM)
        _uiState.update { it.copy(radiusKm = clamped) }
        refreshEstimate()
    }

    fun onRegionNameChanged(name: String) {
        _uiState.update {
            it.copy(
                regionName = name.take(40),
                nameError = validateName(name)
            )
        }
    }

    fun refreshEstimate() {
        val state = _uiState.value
        val estimate = PrepareEstimator.estimate(state.centerLat, state.centerLon, state.radiusKm * 1000)
        _uiState.update {
            it.copy(
                estimateBytes = estimate.totalEstimateBytes,
                demTileCount = estimate.demTileCount,
                step = PrepareStep.ESTIMATE
            )
        }
    }

    fun onDownloadClicked() {
        val state = _uiState.value
        val nameError = validateName(state.regionName)
        if (nameError != null) {
            _uiState.update { it.copy(nameError = nameError) }
            return
        }
        if (!PrepareNetworkMonitor.isNetworkAvailable(context)) {
            _uiState.update {
                it.copy(
                    downloadUiState = PrepareDownloadUiState.ERROR,
                    errorMessage = "No network connection. Connect to Wi-Fi or mobile data to download."
                )
            }
            return
        }
        viewModelScope.launch {
            val wifiOnly = downloadPreferences.wifiOnlyDownloads.first()
            val onCellular = PrepareNetworkMonitor.isCellularNetwork(context)
            if (wifiOnly && onCellular) {
                _uiState.update {
                    it.copy(
                        downloadUiState = PrepareDownloadUiState.ERROR,
                        errorMessage = "Wi-Fi only downloads are enabled. Connect to Wi-Fi to continue."
                    )
                }
                return@launch
            }
            if (onCellular && state.estimateBytes > AppConfig.CELLULAR_DOWNLOAD_WARNING_BYTES) {
                _uiState.update { it.copy(showCellularWarning = true) }
                return@launch
            }
            startDownload()
        }
    }

    fun confirmCellularDownload() {
        _uiState.update { it.copy(showCellularWarning = false) }
        startDownload()
    }

    fun dismissCellularWarning() {
        _uiState.update { it.copy(showCellularWarning = false) }
    }

    fun cancelDownload() {
        val regionId = _uiState.value.regionId ?: return
        downloadScheduler.cancel(regionId)
        orchestrator.cancel()
        viewModelScope.launch {
            val region = regionRepository.getRegion(regionId)
            val status =
                when (region?.downloadStatus) {
                    DownloadStatus.READY, DownloadStatus.PARTIAL -> DownloadStatus.PARTIAL
                    else -> DownloadStatus.NOT_DOWNLOADED
                }
            regionRepository.updateDownloadStatus(regionId, status, _uiState.value.progress?.overallPercent ?: 0)
            _uiState.update {
                it.copy(
                    downloadUiState = PrepareDownloadUiState.PAUSED,
                    step = PrepareStep.ESTIMATE
                )
            }
        }
    }

    fun retryDownload() {
        _uiState.update { it.copy(errorMessage = null, downloadUiState = PrepareDownloadUiState.IDLE) }
        onDownloadClicked()
    }

    private fun startDownload() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    downloadUiState = PrepareDownloadUiState.ESTIMATING,
                    errorMessage = null,
                    step = PrepareStep.DOWNLOAD
                )
            }

            val state = _uiState.value
            val regionId = state.regionId ?: createRegionRecord().also { id ->
                _uiState.update { it.copy(regionId = id) }
            }

            downloadScheduler.enqueue(regionId)
            _uiState.update {
                it.copy(
                    regionId = regionId,
                    downloadUiState = PrepareDownloadUiState.DOWNLOADING
                )
            }

            downloadScheduler.observeWorkState(regionId).collect { workState ->
                when (workState) {
                    PrepareWorkState.SUCCEEDED -> {
                        val region = regionRepository.getRegion(regionId)
                        _uiState.update {
                            it.copy(
                                downloadUiState = PrepareDownloadUiState.COMPLETE,
                                step = PrepareStep.COMPLETE,
                                existingRegion = region
                            )
                        }
                    }
                    PrepareWorkState.FAILED -> {
                        val region = regionRepository.getRegion(regionId)
                        _uiState.update {
                            it.copy(
                                downloadUiState = PrepareDownloadUiState.ERROR,
                                errorMessage = "Download failed. You can retry to continue.",
                                existingRegion = region
                            )
                        }
                    }
                    PrepareWorkState.CANCELLED -> {
                        _uiState.update { it.copy(downloadUiState = PrepareDownloadUiState.PAUSED) }
                    }
                    else -> Unit
                }
            }
        }
    }

    private suspend fun createRegionRecord(): UUID {
        val state = _uiState.value
        val id = UUID.randomUUID()
        val now = Instant.now()
        val bbox = RegionBounds.boundingBox(state.centerLat, state.centerLon, state.radiusKm * 1000)
        val estimate = PrepareEstimator.estimate(state.centerLat, state.centerLon, state.radiusKm * 1000)
        regionRepository.createRegion(
            Region(
                id = id,
                name = state.regionName.trim(),
                centerLat = state.centerLat,
                centerLon = state.centerLon,
                radiusM = state.radiusKm * 1000,
                minLat = bbox.minLat,
                maxLat = bbox.maxLat,
                minLon = bbox.minLon,
                maxLon = bbox.maxLon,
                createdAt = now,
                updatedAt = now,
                downloadStatus = DownloadStatus.NOT_DOWNLOADED,
                downloadProgressPct = 0,
                osmDatasetVersion = null,
                estimatedSizeBytes = estimate.totalEstimateBytes,
                entityCount = 0
            )
        )
        return id
    }

    private fun loadExistingRegion(regionId: UUID) {
        viewModelScope.launch {
            val region = regionRepository.getRegion(regionId) ?: return@launch
            _uiState.update {
                it.copy(
                    regionId = region.id,
                    existingRegion = region,
                    regionName = region.name,
                    centerLat = region.centerLat,
                    centerLon = region.centerLon,
                    radiusKm = region.radiusM / 1000.0,
                    estimateBytes = region.estimatedSizeBytes,
                    downloadUiState =
                    when (region.downloadStatus) {
                        DownloadStatus.READY -> PrepareDownloadUiState.COMPLETE
                        DownloadStatus.DOWNLOADING -> PrepareDownloadUiState.DOWNLOADING
                        DownloadStatus.PARTIAL -> PrepareDownloadUiState.ERROR
                        DownloadStatus.NOT_DOWNLOADED -> PrepareDownloadUiState.IDLE
                    },
                    step =
                    when (region.downloadStatus) {
                        DownloadStatus.READY -> PrepareStep.COMPLETE
                        DownloadStatus.DOWNLOADING -> PrepareStep.DOWNLOAD
                        else -> PrepareStep.ESTIMATE
                    },
                    progress =
                    if (region.downloadStatus == DownloadStatus.DOWNLOADING) {
                        PrepareProgress(
                            phase = PreparePhase.OSM,
                            overallPercent = region.downloadProgressPct
                        )
                    } else {
                        null
                    }
                )
            }
            if (region.downloadStatus == DownloadStatus.DOWNLOADING) {
                downloadScheduler.observeWorkState(regionId).collect { /* handled in startDownload */ }
            }
        }
    }

    private fun validateName(name: String): String? = when {
        name.isBlank() -> "Name is required"
        name.length > 40 -> "Name must be 40 characters or fewer"
        else -> null
    }
}
