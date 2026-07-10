@file:Suppress("TooManyFunctions")

package com.nofar.feature.prepare

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nofar.core.data.network.NetworkConnectivityMonitor
import com.nofar.core.data.preferences.UserPreferencesRepository
import com.nofar.core.data.prepare.PrepareDownloadOrchestrator
import com.nofar.core.data.prepare.PrepareDownloadScheduler
import com.nofar.core.data.prepare.PrepareEstimator
import com.nofar.core.data.prepare.PreparePhase
import com.nofar.core.data.prepare.PrepareProgress
import com.nofar.core.data.prepare.PrepareWorkState
import com.nofar.core.data.prepare.RegionNamePolicy
import com.nofar.core.data.repository.RegionRepository
import com.nofar.core.location.LocationController
import com.nofar.core.location.LocationRepository
import com.nofar.core.model.AppConfig
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.LocationAccessState
import com.nofar.core.model.Region
import com.nofar.core.model.RegionBounds
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val showWifiOnlyBlocked: Boolean = false,
    val nameError: String? = null,
    val mapRecenterNonce: Long = 0L,
    val waitingForGpsFix: Boolean = false,
    val locationAccessState: LocationAccessState = LocationAccessState.NOT_REQUESTED
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
    private val regionRepository: RegionRepository,
    private val downloadScheduler: PrepareDownloadScheduler,
    private val orchestrator: PrepareDownloadOrchestrator,
    private val downloadPreferences: UserPreferencesRepository,
    private val networkConnectivityMonitor: NetworkConnectivityMonitor,
    private val locationRepository: LocationRepository,
    private val locationController: LocationController,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(PrepareUiState())
    val uiState: StateFlow<PrepareUiState> = _uiState.asStateFlow()
    private val editingExistingRegion =
        savedStateHandle.get<String>("regionId")?.takeIf { it.isNotBlank() } != null
    private var hasSetInitialLocation = false
    private var pendingRecenterOnLocation = false

    init {
        locationController.acquire(PREPARE_LOCATION_TOKEN)
        savedStateHandle.get<String>("regionId")?.takeIf { it.isNotBlank() }?.let { id ->
            loadExistingRegion(UUID.fromString(id))
            hasSetInitialLocation = true
        }
        if (!editingExistingRegion) {
            viewModelScope.launch {
                locationRepository.lastLocation?.let { location ->
                    setRegionCenter(
                        lat = location.latitude,
                        lon = location.longitude,
                        recenterMap = true,
                        suggestName = true
                    )
                    hasSetInitialLocation = true
                }
                locationRepository.locationFlow.collect { location ->
                    if (pendingRecenterOnLocation || !hasSetInitialLocation) {
                        setRegionCenter(
                            lat = location.latitude,
                            lon = location.longitude,
                            recenterMap = true,
                            suggestName = !hasSetInitialLocation
                        )
                        pendingRecenterOnLocation = false
                        hasSetInitialLocation = true
                    }
                }
            }
        }
        refreshEstimate()
        viewModelScope.launch {
            orchestrator.progress.collect { progress ->
                if (progress == null) return@collect
                _uiState.update { state ->
                    if (state.downloadUiState == PrepareDownloadUiState.COMPLETE ||
                        state.downloadUiState == PrepareDownloadUiState.ERROR
                    ) {
                        state.copy(progress = progress)
                    } else {
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
        val regionId = _uiState.value.regionId ?: return
        val region = regionRepository.getRegion(regionId) ?: return
        when (region.downloadStatus) {
            DownloadStatus.DOWNLOADING -> {
                _uiState.update { state ->
                    if (state.downloadUiState == PrepareDownloadUiState.COMPLETE ||
                        state.downloadUiState == PrepareDownloadUiState.ERROR
                    ) {
                        state
                    } else {
                        val syncedName =
                            if (RegionNamePolicy.isUserProvidedName(state.regionName)) {
                                state.regionName
                            } else {
                                region.name
                            }
                        state.copy(
                            regionName = syncedName,
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
            DownloadStatus.READY, DownloadStatus.PARTIAL -> {
                if (_uiState.value.downloadUiState == PrepareDownloadUiState.DOWNLOADING ||
                    _uiState.value.downloadUiState == PrepareDownloadUiState.ESTIMATING
                ) {
                    markDownloadComplete(region)
                }
            }
            else -> Unit
        }
    }

    fun onLocationPermissionChanged(accessState: LocationAccessState) {
        if (accessState == LocationAccessState.GRANTED) {
            locationRepository.start()
            locationRepository.lastLocation?.let { location ->
                if (!hasSetInitialLocation && !editingExistingRegion) {
                    setRegionCenter(
                        lat = location.latitude,
                        lon = location.longitude,
                        recenterMap = true,
                        suggestName = true
                    )
                    hasSetInitialLocation = true
                } else if (pendingRecenterOnLocation) {
                    setRegionCenter(
                        lat = location.latitude,
                        lon = location.longitude,
                        recenterMap = true,
                        suggestName = false
                    )
                    pendingRecenterOnLocation = false
                }
            }
        } else {
            pendingRecenterOnLocation = false
        }
        _uiState.update {
            val waiting =
                accessState == LocationAccessState.GRANTED &&
                    locationRepository.lastLocation == null &&
                    pendingRecenterOnLocation
            it.copy(
                locationAccessState = if (waiting) LocationAccessState.WAITING_FOR_FIX else accessState,
                waitingForGpsFix = waiting
            )
        }
    }

    fun moveToCurrentLocation() {
        val location = locationRepository.lastLocation
        if (location != null) {
            setRegionCenter(
                lat = location.latitude,
                lon = location.longitude,
                recenterMap = true,
                suggestName = false
            )
        } else {
            pendingRecenterOnLocation = true
            _uiState.update {
                it.copy(
                    waitingForGpsFix = true,
                    locationAccessState = LocationAccessState.WAITING_FOR_FIX
                )
            }
        }
    }

    fun onMapTap(lat: Double, lon: Double) {
        setRegionCenter(lat = lat, lon = lon, recenterMap = false, suggestName = true)
    }

    private fun setRegionCenter(lat: Double, lon: Double, recenterMap: Boolean, suggestName: Boolean) {
        _uiState.update {
            val name =
                if (suggestName && it.regionName.isBlank()) {
                    "Region near ${"%.2f".format(lat)}, ${"%.2f".format(lon)}"
                } else {
                    it.regionName
                }
            it.copy(
                centerLat = lat,
                centerLon = lon,
                regionName = name,
                mapRecenterNonce = if (recenterMap) it.mapRecenterNonce + 1 else it.mapRecenterNonce,
                waitingForGpsFix = false,
                locationAccessState =
                if (it.locationAccessState == LocationAccessState.WAITING_FOR_FIX) {
                    LocationAccessState.GRANTED
                } else {
                    it.locationAccessState
                },
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
        if (!networkConnectivityMonitor.isNetworkAvailable()) {
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
            val onCellular = networkConnectivityMonitor.isCellularNetwork()
            when (
                val gate =
                    PrepareDownloadPolicy.evaluateStart(
                        networkAvailable = true,
                        wifiOnlyDownloads = wifiOnly,
                        onCellularNetwork = onCellular,
                        estimateBytes = state.estimateBytes
                    )
            ) {
                is PrepareDownloadPolicy.GateResult.Blocked -> {
                    if (wifiOnly && onCellular) {
                        _uiState.update { it.copy(showWifiOnlyBlocked = true) }
                    } else {
                        _uiState.update {
                            it.copy(
                                downloadUiState = PrepareDownloadUiState.ERROR,
                                errorMessage = gate.message
                            )
                        }
                    }
                }
                PrepareDownloadPolicy.GateResult.CellularWarning -> {
                    _uiState.update { it.copy(showCellularWarning = true) }
                }
                PrepareDownloadPolicy.GateResult.Proceed -> startDownload()
            }
        }
    }

    fun confirmCellularDownload() {
        _uiState.update { it.copy(showCellularWarning = false) }
        startDownload()
    }

    fun dismissCellularWarning() {
        _uiState.update { it.copy(showCellularWarning = false) }
    }

    fun dismissWifiOnlyBlocked() {
        _uiState.update { it.copy(showWifiOnlyBlocked = false) }
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
            val regionId =
                state.regionId ?: UUID.randomUUID().also { id ->
                    _uiState.update { it.copy(regionId = id) }
                }
            syncRegionRecord(regionId)

            downloadScheduler.enqueue(regionId)
            _uiState.update {
                it.copy(
                    regionId = regionId,
                    downloadUiState = PrepareDownloadUiState.DOWNLOADING
                )
            }

            downloadScheduler.observeWorkState(regionId).collect { workState ->
                applyWorkState(regionId, workState)
            }
        }
    }

    private suspend fun syncRegionRecord(regionId: UUID) {
        val state = _uiState.value
        val now = Instant.now()
        val radiusM = state.radiusKm * 1000
        val bbox = RegionBounds.boundingBox(state.centerLat, state.centerLon, radiusM)
        val estimate = PrepareEstimator.estimate(state.centerLat, state.centerLon, radiusM)
        val existing = regionRepository.getRegion(regionId)
        val region =
            Region(
                id = regionId,
                name = state.regionName.trim(),
                centerLat = state.centerLat,
                centerLon = state.centerLon,
                radiusM = radiusM,
                minLat = bbox.minLat,
                maxLat = bbox.maxLat,
                minLon = bbox.minLon,
                maxLon = bbox.maxLon,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                downloadStatus = existing?.downloadStatus ?: DownloadStatus.NOT_DOWNLOADED,
                downloadProgressPct = existing?.downloadProgressPct ?: 0,
                osmDatasetVersion = existing?.osmDatasetVersion,
                estimatedSizeBytes = estimate.totalEstimateBytes,
                entityCount = existing?.entityCount ?: 0
            )
        if (existing == null) {
            regionRepository.createRegion(region)
        } else {
            regionRepository.updateRegion(region)
        }
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
                downloadScheduler.observeWorkState(regionId).collect { workState ->
                    applyWorkState(regionId, workState)
                }
            }
        }
    }

    private suspend fun applyWorkState(regionId: UUID, workState: PrepareWorkState?) {
        when (workState) {
            PrepareWorkState.SUCCEEDED -> {
                val region = regionRepository.getRegion(regionId)
                if (region != null) {
                    markDownloadComplete(region)
                }
            }
            PrepareWorkState.FAILED -> {
                val region = regionRepository.getRegion(regionId)
                when (region?.downloadStatus) {
                    DownloadStatus.READY, DownloadStatus.PARTIAL -> markDownloadComplete(region)
                    else ->
                        _uiState.update {
                            it.copy(
                                downloadUiState = PrepareDownloadUiState.ERROR,
                                step = PrepareStep.ESTIMATE,
                                errorMessage = "Download failed. You can retry to continue.",
                                existingRegion = region,
                                progress = null
                            )
                        }
                }
            }
            PrepareWorkState.CANCELLED -> {
                _uiState.update {
                    it.copy(
                        downloadUiState = PrepareDownloadUiState.PAUSED,
                        step = PrepareStep.ESTIMATE,
                        progress = null
                    )
                }
            }
            else -> Unit
        }
    }

    private fun markDownloadComplete(region: Region) {
        _uiState.update {
            val syncedName =
                if (RegionNamePolicy.isUserProvidedName(it.regionName)) {
                    it.regionName
                } else {
                    region.name
                }
            it.copy(
                regionName = syncedName,
                downloadUiState = PrepareDownloadUiState.COMPLETE,
                step = PrepareStep.COMPLETE,
                existingRegion = region,
                progress = null,
                errorMessage = null
            )
        }
    }

    private fun validateName(name: String): String? = when {
        name.isBlank() -> "Name is required"
        name.length > 40 -> "Name must be 40 characters or fewer"
        else -> null
    }

    override fun onCleared() {
        locationController.release(PREPARE_LOCATION_TOKEN)
        super.onCleared()
    }

    companion object {
        private const val PREPARE_LOCATION_TOKEN = "prepare"
    }
}
