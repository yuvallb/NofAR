@file:Suppress("TooManyFunctions", "LargeClass", "ReturnCount", "LongMethod")

package com.nofar.feature.prepare

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nofar.core.data.network.NetworkConnectivityMonitor
import com.nofar.core.data.preferences.UserPreferencesRepository
import com.nofar.core.data.prepare.CoverageNameResolver
import com.nofar.core.data.prepare.DownloadPolicy
import com.nofar.core.data.prepare.PrepareDownloadError
import com.nofar.core.data.prepare.PrepareDownloadOrchestrator
import com.nofar.core.data.prepare.PrepareDownloadScheduler
import com.nofar.core.data.prepare.PrepareEstimator
import com.nofar.core.data.prepare.PreparePhase
import com.nofar.core.data.prepare.PrepareProgress
import com.nofar.core.data.prepare.PrepareWorkState
import com.nofar.core.data.prepare.RegionNamePolicy
import com.nofar.core.data.repository.CoverageSetRepository
import com.nofar.core.data.repository.HomeCoverageSetMetadataRepository
import com.nofar.core.data.usecase.QuickCoverageDownloadUseCase
import com.nofar.core.location.LocationController
import com.nofar.core.location.LocationRepository
import com.nofar.core.model.AppConfig
import com.nofar.core.model.CellMembership
import com.nofar.core.model.CoverageSet
import com.nofar.core.model.DemTileId
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.LabelLanguage
import com.nofar.core.model.LocationAccessState
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
    val coverageSetName: String = "",
    val centerLat: Double = 32.0,
    val centerLon: Double = 35.0,
    val selectedCellIds: Set<String> = emptySet(),
    val coverageSetId: UUID? = null,
    val existingCoverageSet: CoverageSet? = null,
    /** READY/PARTIAL regions drawn on the map (excludes the region being edited). */
    val downloadedCoverageSets: List<CoverageSet> = emptyList(),
    val downloadedCellIdsBySet: Map<UUID, List<String>> = emptyMap(),
    val labelLanguage: LabelLanguage = LabelLanguage.DEFAULT,
    val labelLanguageLocked: Boolean = false,
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
    private val coverageSetRepository: CoverageSetRepository,
    private val metadataRepository: HomeCoverageSetMetadataRepository,
    private val quickCoverageDownloadUseCase: QuickCoverageDownloadUseCase,
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
        savedStateHandle.get<String>("coverageSetId")?.takeIf { it.isNotBlank() } != null
    private var hasSetInitialLocation = false
    private var pendingRecenterOnLocation = false

    init {
        locationController.acquire(PREPARE_LOCATION_TOKEN)
        observeDownloadedRegions()
        savedStateHandle.get<String>("coverageSetId")?.takeIf { it.isNotBlank() }?.let { id ->
            loadExistingRegion(UUID.fromString(id))
            hasSetInitialLocation = true
        }
        if (!editingExistingRegion) {
            applyProposedRegionFromArgs(savedStateHandle)
            viewModelScope.launch {
                val preferredLanguage = downloadPreferences.preferredLabelLanguage.first()
                _uiState.update { it.copy(labelLanguage = preferredLanguage, labelLanguageLocked = false) }
                if (!hasSetInitialLocation) {
                    locationRepository.lastLocation?.let { location ->
                        setRegionCenter(
                            lat = location.latitude,
                            lon = location.longitude,
                            recenterMap = true,
                            suggestName = true,
                            seedLocalCellsIfEmpty = true
                        )
                        hasSetInitialLocation = true
                    }
                }
                locationRepository.locationFlow.collect { location ->
                    if (pendingRecenterOnLocation || !hasSetInitialLocation) {
                        setRegionCenter(
                            lat = location.latitude,
                            lon = location.longitude,
                            recenterMap = true,
                            suggestName = !hasSetInitialLocation,
                            seedLocalCellsIfEmpty = true
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
                _uiState.update { state -> applyLiveDownloadProgress(state, progress) }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(500)
                pollDownloadProgressFromDb()
            }
        }
    }

    private fun applyProposedRegionFromArgs(savedStateHandle: SavedStateHandle) {
        val centerLat = PrepareRouteBuilder.parseDouble(savedStateHandle.get("centerLat"))
        val centerLon = PrepareRouteBuilder.parseDouble(savedStateHandle.get("centerLon"))
        if (centerLat == null || centerLon == null) return
        val name = PrepareRouteBuilder.parseName(savedStateHandle.get("name"))
        setRegionCenter(
            lat = centerLat,
            lon = centerLon,
            recenterMap = true,
            suggestName = name.isNullOrBlank(),
            seedLocalCellsIfEmpty = true
        )
        if (!name.isNullOrBlank()) {
            _uiState.update { it.copy(coverageSetName = name.take(40)) }
        }
        hasSetInitialLocation = true
    }

    private fun observeDownloadedRegions() {
        viewModelScope.launch {
            coverageSetRepository.observeAllCoverageSets().collect { regions ->
                val downloaded =
                    regions.filter { region ->
                        region.downloadStatus == DownloadStatus.READY ||
                            region.downloadStatus == DownloadStatus.PARTIAL
                    }
                val cellIdsBySet =
                    downloaded.associate { set ->
                        set.id to coverageSetRepository.getCellIdsForCoverageSet(set.id)
                    }
                _uiState.update {
                    it.copy(
                        downloadedCoverageSets = downloaded,
                        downloadedCellIdsBySet = cellIdsBySet
                    )
                }
            }
        }
    }

    private suspend fun pollDownloadProgressFromDb() {
        val coverageSetId = _uiState.value.coverageSetId ?: return
        val region = coverageSetRepository.getCoverageSet(coverageSetId) ?: return
        when (region.downloadStatus) {
            DownloadStatus.DOWNLOADING -> {
                _uiState.update { state ->
                    if (state.downloadUiState == PrepareDownloadUiState.COMPLETE ||
                        state.downloadUiState == PrepareDownloadUiState.ERROR
                    ) {
                        state
                    } else {
                        val syncedName =
                            if (RegionNamePolicy.isUserProvidedName(state.coverageSetName)) {
                                state.coverageSetName
                            } else {
                                region.name
                            }
                        state.copy(
                            coverageSetName = syncedName,
                            progress =
                            state.progress?.copy(overallPercent = region.downloadProgressPct)
                                ?: PrepareProgress(
                                    coverageSetId = coverageSetId,
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
                        suggestName = true,
                        seedLocalCellsIfEmpty = true
                    )
                    hasSetInitialLocation = true
                } else if (pendingRecenterOnLocation) {
                    setRegionCenter(
                        lat = location.latitude,
                        lon = location.longitude,
                        recenterMap = true,
                        suggestName = false,
                        seedLocalCellsIfEmpty = true
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
                suggestName = false,
                seedLocalCellsIfEmpty = true
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
        val clampedLat = lat.coerceIn(AppConfig.MAP_CENTER_LAT_MIN, AppConfig.MAP_CENTER_LAT_MAX)
        val clampedLon = lon.coerceIn(AppConfig.MAP_CENTER_LON_MIN, AppConfig.MAP_CENTER_LON_MAX)
        val cellId = CellMembership.cellIdForPoint(clampedLat, clampedLon)
        _uiState.update { state ->
            val selectedCellIds =
                if (cellId in state.selectedCellIds) {
                    state.selectedCellIds - cellId
                } else {
                    state.selectedCellIds + cellId
                }
            val name =
                if (state.coverageSetName.isBlank()) {
                    RegionNamePolicy.formatAutoName(clampedLat, clampedLon)
                } else {
                    state.coverageSetName
                }
            state.copy(
                centerLat = clampedLat,
                centerLon = clampedLon,
                selectedCellIds = selectedCellIds,
                coverageSetName = name,
                step = PrepareStep.DEFINE,
                errorMessage = null
            )
        }
        refreshEstimate()
    }

    private fun setRegionCenter(
        lat: Double,
        lon: Double,
        recenterMap: Boolean,
        suggestName: Boolean,
        seedLocalCellsIfEmpty: Boolean = false
    ) {
        val clampedLat = lat.coerceIn(AppConfig.MAP_CENTER_LAT_MIN, AppConfig.MAP_CENTER_LAT_MAX)
        val clampedLon = lon.coerceIn(AppConfig.MAP_CENTER_LON_MIN, AppConfig.MAP_CENTER_LON_MAX)
        _uiState.update {
            val name =
                if (suggestName && it.coverageSetName.isBlank()) {
                    RegionNamePolicy.formatAutoName(clampedLat, clampedLon)
                } else {
                    it.coverageSetName
                }
            it.copy(
                centerLat = clampedLat,
                centerLon = clampedLon,
                selectedCellIds =
                if (seedLocalCellsIfEmpty && it.selectedCellIds.isEmpty()) {
                    CellMembership.localDownloadCellIds(clampedLat, clampedLon).toSet()
                } else {
                    it.selectedCellIds
                },
                coverageSetName = name,
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

    fun onRegionNameChanged(name: String) {
        _uiState.update {
            it.copy(
                coverageSetName = name.take(40),
                nameError = validateName(name)
            )
        }
    }

    fun onLabelLanguageChanged(language: LabelLanguage) {
        if (_uiState.value.labelLanguageLocked) return
        _uiState.update { it.copy(labelLanguage = language) }
        viewModelScope.launch {
            downloadPreferences.setPreferredLabelLanguage(language)
        }
    }

    fun refreshEstimate() {
        val state = _uiState.value
        val cells = state.selectedCellIds.mapNotNull(DemTileId::parse)
        val estimate = cells.takeIf { it.isNotEmpty() }?.let(PrepareEstimator::estimateForCells)
        _uiState.update {
            it.copy(
                estimateBytes = estimate?.totalEstimateBytes ?: 0L,
                demTileCount = estimate?.demTileCount ?: 0,
                step = PrepareStep.ESTIMATE
            )
        }
    }

    fun onDownloadClicked() {
        val state = _uiState.value
        val nameError = validateName(state.coverageSetName)
        if (nameError != null) {
            _uiState.update { it.copy(nameError = nameError) }
            return
        }
        if (state.selectedCellIds.isEmpty()) {
            _uiState.update {
                it.copy(
                    downloadUiState = PrepareDownloadUiState.ERROR,
                    errorMessage = "Select at least one map cell before downloading."
                )
            }
            return
        }
        if (!networkConnectivityMonitor.isNetworkAvailable()) {
            _uiState.update {
                it.copy(
                    downloadUiState = PrepareDownloadUiState.ERROR,
                    errorMessage = PrepareDownloadError.NoNetwork.toUserMessage()
                )
            }
            return
        }
        viewModelScope.launch {
            val wifiOnly = downloadPreferences.wifiOnlyDownloads.first()
            val onCellular = networkConnectivityMonitor.isCellularNetwork()
            when (
                val gate =
                    DownloadPolicy.evaluateStart(
                        networkAvailable = true,
                        wifiOnlyDownloads = wifiOnly,
                        onCellularNetwork = onCellular,
                        estimateBytes = state.estimateBytes
                    )
            ) {
                is DownloadPolicy.GateResult.Blocked -> {
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
                DownloadPolicy.GateResult.CellularWarning -> {
                    _uiState.update { it.copy(showCellularWarning = true) }
                }
                DownloadPolicy.GateResult.Proceed -> startDownload()
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
        val coverageSetId = _uiState.value.coverageSetId ?: return
        downloadScheduler.cancel(coverageSetId)
        orchestrator.cancel()
        viewModelScope.launch {
            val region = coverageSetRepository.getCoverageSet(coverageSetId)
            // Query coverage without region geometry so we only count linked tiles, not expected ones.
            val metadata = metadataRepository.getMetadata(coverageSetId)
            val status =
                PrepareCancelStatus.resolve(
                    region = region,
                    progress = _uiState.value.progress,
                    hasTileCoverage = metadata.tileCount > 0,
                    liveEntityCount = metadata.liveEntityCount
                )
            coverageSetRepository.updateDownloadStatus(
                coverageSetId,
                status,
                _uiState.value.progress?.overallPercent ?: 0
            )
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
            val coverageSetId =
                state.coverageSetId ?: UUID.randomUUID().also { id ->
                    _uiState.update { it.copy(coverageSetId = id) }
                }
            // Ensure the worker reads the language selected in Prepare, not a stale Settings value.
            downloadPreferences.setPreferredLabelLanguage(state.labelLanguage)
            val cellIds = state.selectedCellIds.sorted()
            val coverageSet =
                runCatching { buildCoverageSetRecord(coverageSetId, cellIds) }.getOrElse { error ->
                    showDownloadStartError(error)
                    return@launch
                }
            quickCoverageDownloadUseCase.syncAndEnqueue(coverageSet, cellIds).getOrElse { error ->
                showDownloadStartError(error)
                return@launch
            }
            // Keep UI language in sync with what was persisted for this download.
            _uiState.update {
                it.copy(
                    coverageSetId = coverageSetId,
                    existingCoverageSet = coverageSet.copy(downloadStatus = DownloadStatus.DOWNLOADING),
                    downloadUiState = PrepareDownloadUiState.DOWNLOADING
                )
            }

            downloadScheduler.observeWorkState(coverageSetId).collect { workState ->
                applyWorkState(coverageSetId, workState)
            }
        }
    }

    private suspend fun buildCoverageSetRecord(coverageSetId: UUID, cellIds: List<String>): CoverageSet {
        val state = _uiState.value
        val now = Instant.now()
        require(cellIds.isNotEmpty()) { "Select at least one map cell before downloading." }
        val cells =
            cellIds.map { cellId ->
                requireNotNull(DemTileId.parse(cellId)) { "Invalid coverage cell: $cellId" }
            }
        val estimate = PrepareEstimator.estimateForCells(cells)
        val existing = coverageSetRepository.getCoverageSet(coverageSetId)
        return CoverageSet(
            id = coverageSetId,
            name = state.coverageSetName.trim(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            downloadStatus = existing?.downloadStatus ?: DownloadStatus.NOT_DOWNLOADED,
            downloadProgressPct = existing?.downloadProgressPct ?: 0,
            osmDatasetVersion = existing?.osmDatasetVersion,
            estimatedSizeBytes = estimate.totalEstimateBytes,
            entityCount = existing?.entityCount ?: 0,
            labelLanguage = state.labelLanguage
        )
    }

    private fun loadExistingRegion(coverageSetId: UUID) {
        viewModelScope.launch {
            val region = coverageSetRepository.getCoverageSet(coverageSetId) ?: return@launch
            val cellIds = coverageSetRepository.getCellIdsForCoverageSet(coverageSetId)
            val (centerLat, centerLon) =
                cellIds.takeIf { it.isNotEmpty() }?.let(CoverageNameResolver::referenceCenterFromCellIds)
                    ?: (_uiState.value.centerLat to _uiState.value.centerLon)
            val estimate =
                cellIds.mapNotNull(DemTileId::parse)
                    .takeIf { it.isNotEmpty() }
                    ?.let(PrepareEstimator::estimateForCells)
            // Prefer Settings default for the next download; Explore keeps the stored language
            // until this region is re-downloaded.
            val preferredLanguage = downloadPreferences.preferredLabelLanguage.first()
            _uiState.update {
                it.copy(
                    coverageSetId = region.id,
                    existingCoverageSet = region,
                    coverageSetName = region.name,
                    centerLat = centerLat,
                    centerLon = centerLon,
                    selectedCellIds = cellIds.toSet(),
                    estimateBytes = estimate?.totalEstimateBytes ?: region.estimatedSizeBytes,
                    demTileCount = estimate?.demTileCount ?: 0,
                    mapRecenterNonce = it.mapRecenterNonce + 1,
                    labelLanguage = preferredLanguage,
                    labelLanguageLocked = region.downloadStatus == DownloadStatus.DOWNLOADING,
                    downloadUiState =
                    when (region.downloadStatus) {
                        DownloadStatus.READY, DownloadStatus.PARTIAL -> PrepareDownloadUiState.COMPLETE
                        DownloadStatus.DOWNLOADING -> PrepareDownloadUiState.DOWNLOADING
                        DownloadStatus.NOT_DOWNLOADED -> PrepareDownloadUiState.IDLE
                    },
                    errorMessage =
                    if (region.downloadStatus == DownloadStatus.PARTIAL) {
                        "Download complete with partial DEM or elevation coverage. Retry to fill gaps."
                    } else {
                        null
                    },
                    step =
                    when (region.downloadStatus) {
                        DownloadStatus.READY, DownloadStatus.PARTIAL -> PrepareStep.COMPLETE
                        DownloadStatus.DOWNLOADING -> PrepareStep.DOWNLOAD
                        else -> PrepareStep.ESTIMATE
                    },
                    progress =
                    if (region.downloadStatus == DownloadStatus.DOWNLOADING) {
                        PrepareProgress(
                            coverageSetId = region.id,
                            phase = PreparePhase.OSM,
                            overallPercent = region.downloadProgressPct
                        )
                    } else {
                        null
                    }
                )
            }
            if (region.downloadStatus == DownloadStatus.DOWNLOADING) {
                downloadScheduler.observeWorkState(coverageSetId).collect { workState ->
                    applyWorkState(coverageSetId, workState)
                }
            }
        }
    }

    private fun showDownloadStartError(error: Throwable) {
        _uiState.update {
            it.copy(
                downloadUiState = PrepareDownloadUiState.ERROR,
                step = PrepareStep.ESTIMATE,
                errorMessage = error.message ?: "Unable to start download."
            )
        }
    }

    private suspend fun applyWorkState(coverageSetId: UUID, workState: PrepareWorkState?) {
        when (workState) {
            PrepareWorkState.SUCCEEDED -> {
                val region = coverageSetRepository.getCoverageSet(coverageSetId)
                if (region != null) {
                    markDownloadComplete(region)
                }
            }
            PrepareWorkState.FAILED -> {
                val region = coverageSetRepository.getCoverageSet(coverageSetId)
                when (region?.downloadStatus) {
                    DownloadStatus.READY, DownloadStatus.PARTIAL -> markDownloadComplete(region)
                    else -> {
                        val typed =
                            orchestrator.lastError.value
                                ?: PrepareDownloadError.Unknown("Download failed. You can retry to continue.")
                        _uiState.update {
                            it.copy(
                                downloadUiState = PrepareDownloadUiState.ERROR,
                                step = PrepareStep.ESTIMATE,
                                errorMessage = typed.toUserMessage(),
                                existingCoverageSet = region,
                                progress = null
                            )
                        }
                        orchestrator.clearLastError()
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

    private fun markDownloadComplete(region: CoverageSet) {
        _uiState.update {
            val syncedName =
                if (RegionNamePolicy.isUserProvidedName(it.coverageSetName)) {
                    it.coverageSetName
                } else {
                    region.name
                }
            it.copy(
                coverageSetName = syncedName,
                downloadUiState = PrepareDownloadUiState.COMPLETE,
                step = PrepareStep.COMPLETE,
                existingCoverageSet = region,
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
