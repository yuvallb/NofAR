@file:Suppress("TooManyFunctions")

package com.nofar.feature.explore

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nofar.core.data.network.NetworkConnectivityMonitor
import com.nofar.core.data.preferences.UserPreferencesRepository
import com.nofar.core.data.prepare.DownloadPolicy
import com.nofar.core.data.prepare.PrepareDownloadScheduler
import com.nofar.core.data.repository.RegionRepository
import com.nofar.core.data.usecase.ExploreRegionResolution
import com.nofar.core.data.usecase.ExploreRegionResolver
import com.nofar.core.data.usecase.QuickRegionDownloadUseCase
import com.nofar.core.data.usecase.RegionCoverageRepairUseCase
import com.nofar.core.location.LocationController
import com.nofar.core.location.LocationRepository
import com.nofar.core.model.CompassCalibrationState
import com.nofar.core.model.DeviceOrientation
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.LocationAccessState
import com.nofar.core.model.Region
import com.nofar.core.model.UserLocation
import com.nofar.core.sensors.CompassCalibrationMonitor
import com.nofar.core.sensors.DeclinationCorrector
import com.nofar.core.sensors.OrientationController
import com.nofar.core.sensors.OrientationProvider
import com.nofar.core.sensors.di.UnsmoothedOrientation
import com.nofar.core.visibility.CameraFieldOfView
import com.nofar.core.visibility.DisplayAltitudeResolver
import com.nofar.core.visibility.VisibilityPassScheduler
import com.nofar.core.visibility.VisibilityWarning
import com.nofar.core.visibility.VisibleEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ExploreViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val orientationProvider: OrientationProvider,
    @param:UnsmoothedOrientation private val unsmoothedOrientationProvider: OrientationProvider,
    private val orientationController: OrientationController,
    private val locationRepository: LocationRepository,
    private val locationController: LocationController,
    private val calibrationMonitor: CompassCalibrationMonitor,
    private val declinationCorrector: DeclinationCorrector,
    private val visibilityPassScheduler: VisibilityPassScheduler,
    private val displayAltitudeResolver: DisplayAltitudeResolver,
    private val regionRepository: RegionRepository,
    private val regionCoverageRepairUseCase: RegionCoverageRepairUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val quickRegionDownloadUseCase: QuickRegionDownloadUseCase,
    private val downloadScheduler: PrepareDownloadScheduler,
    private val networkConnectivityMonitor: NetworkConnectivityMonitor
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    private val requestedRegionId: UUID? =
        savedStateHandle.get<String>("regionId")?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private val regionBoundaryController = ExploreRegionBoundaryController()
    private val altitudeController =
        ExploreAltitudeController(
            scope = viewModelScope,
            displayAltitudeResolver = displayAltitudeResolver,
            uiState = _uiState,
            activeRegion = { _uiState.value.activeRegion }
        )
    private val downloadController =
        ExploreDownloadController(
            scope = viewModelScope,
            regionRepository = regionRepository,
            quickRegionDownloadUseCase = quickRegionDownloadUseCase,
            downloadScheduler = downloadScheduler,
            uiState = _uiState,
            onDownloadComplete = { region -> applyActiveRegions(listOf(region)) },
            onRefreshGate = { refreshGate() }
        )
    private var cachedVisibleEntities: List<VisibleEntity> = emptyList()
    private var currentOrientation: DeviceOrientation? = null
    private var currentRawOrientation: DeviceOrientation? = null
    private var hasReceivedOrientation: Boolean = false
    private var lastCompassBearingDeg: Float = 0f

    init {
        locationController.acquire(EXPLORE_LOCATION_TOKEN)
        orientationController.acquire(EXPLORE_ORIENTATION_TOKEN)
        visibilityPassScheduler.start(viewModelScope)
        collectSimpleModePreference()
        viewModelScope.launch { resolveInitialRegion() }
        collectOrientation()
        collectLocation()
        collectVisibility()
        collectDebugPreferences()
    }

    fun onLocationPermissionChanged(accessState: LocationAccessState) {
        if (accessState == LocationAccessState.GRANTED) {
            locationRepository.start()
        } else {
            locationRepository.onPermissionRevoked()
            declinationCorrector.clearSeedLocation()
            visibilityPassScheduler.setActiveRegions(emptyList())
        }
        _uiState.update { state ->
            val waiting =
                accessState == LocationAccessState.GRANTED &&
                    locationRepository.lastLocation == null
            state.copy(
                altitude = if (accessState == LocationAccessState.GRANTED) state.altitude else null,
                locationAccessState = if (waiting) LocationAccessState.WAITING_FOR_FIX else accessState,
                waitingForGpsFix = waiting
            )
        }
        if (accessState == LocationAccessState.GRANTED) {
            locationRepository.lastLocation?.let { location ->
                altitudeController.scheduleResolve(location, _uiState.value.activeRegions)
            }
        } else {
            altitudeController.clearAltitude()
        }
        refreshGate()
    }

    fun onCameraPermissionChanged(granted: Boolean) {
        _uiState.update { it.copy(cameraGranted = granted) }
        refreshGate()
    }

    fun onScreenSizeChanged(widthPx: Float, heightPx: Float) {
        if (widthPx <= 0f || heightPx <= 0f) return
        _uiState.update { it.copy(screenWidthPx = widthPx, screenHeightPx = heightPx) }
        reprojectLabels()
    }

    fun onCameraFieldOfViewChanged(fov: CameraFieldOfView) {
        _uiState.update { it.copy(cameraFov = fov) }
        reprojectLabels()
    }

    fun onHiddenCountClicked(bucketIndex: Int) {
        _uiState.update { it.copy(expandedBucketIndex = bucketIndex) }
        reprojectLabels()
    }

    fun onDismissExpandedBucket() {
        _uiState.update { it.copy(expandedBucketIndex = null, expandedCluster = null) }
        reprojectLabels()
    }

    fun onDownloadRegionConfirmed() {
        val proposal = _uiState.value.downloadPrompt ?: return
        if (!networkConnectivityMonitor.isNetworkAvailable()) {
            _uiState.update {
                it.copy(downloadUiMessage = "No network connection. Connect to Wi-Fi or mobile data to download.")
            }
            return
        }
        viewModelScope.launch {
            val wifiOnly = userPreferencesRepository.wifiOnlyDownloads.first()
            val onCellular = networkConnectivityMonitor.isCellularNetwork()
            when (
                val gate =
                    DownloadPolicy.evaluateStart(
                        networkAvailable = true,
                        wifiOnlyDownloads = wifiOnly,
                        onCellularNetwork = onCellular,
                        estimateBytes = proposal.estimateBytes
                    )
            ) {
                is DownloadPolicy.GateResult.Blocked -> {
                    if (wifiOnly && onCellular) {
                        _uiState.update { it.copy(showWifiOnlyBlocked = true) }
                    } else {
                        _uiState.update { it.copy(downloadUiMessage = gate.message) }
                    }
                }
                DownloadPolicy.GateResult.CellularWarning -> {
                    downloadController.pendingCellularProposal = proposal
                    _uiState.update { it.copy(showCellularWarning = true) }
                }
                DownloadPolicy.GateResult.Proceed -> downloadController.startDownload(proposal)
            }
        }
    }

    fun confirmCellularDownload() {
        val proposal = downloadController.pendingCellularProposal ?: _uiState.value.downloadPrompt ?: return
        downloadController.pendingCellularProposal = null
        _uiState.update { it.copy(showCellularWarning = false) }
        viewModelScope.launch { downloadController.startDownload(proposal) }
    }

    fun dismissCellularWarning() {
        downloadController.pendingCellularProposal = null
        _uiState.update { it.copy(showCellularWarning = false) }
    }

    fun dismissWifiOnlyBlocked() {
        _uiState.update { it.copy(showWifiOnlyBlocked = false) }
    }

    fun onDownloadPromptDismissed() {
        _uiState.update { it.copy(downloadPromptDismissed = true) }
        refreshGate()
    }

    fun onShowDownloadPrompt() {
        _uiState.update { it.copy(downloadPromptDismissed = false) }
        refreshGate()
    }

    override fun onCleared() {
        downloadController.onCleared()
        regionBoundaryController.stopGraceTicker()
        visibilityPassScheduler.stop()
        locationController.release(EXPLORE_LOCATION_TOKEN)
        orientationController.release(EXPLORE_ORIENTATION_TOKEN)
        super.onCleared()
    }

    private fun collectSimpleModePreference() {
        viewModelScope.launch {
            userPreferencesRepository.simpleModeEnabled.collect { enabled ->
                _uiState.update { it.copy(simpleModeEnabled = enabled) }
                refreshGate()
            }
        }
    }

    private suspend fun resolveInitialRegion() {
        val location = locationRepository.lastLocation
        if (location != null) {
            applyRegionResolution(location)
            altitudeController.scheduleResolve(location, _uiState.value.activeRegions)
            return
        }
        if (requestedRegionId != null) {
            val region = regionRepository.getRegion(requestedRegionId)
            applyActiveRegions(listOfNotNull(region))
        }
    }

    private suspend fun applyRegionResolution(location: UserLocation) {
        val state = _uiState.value
        if (state.simpleModeEnabled) {
            val regionsAtPoint =
                regionRepository.regionsContainingPoint(location.latitude, location.longitude)
            val downloadingRegion = regionRepository.findDownloadingRegion()
            val resolution =
                ExploreRegionResolver.resolve(
                    regionsAtPoint = regionsAtPoint,
                    downloadingRegion = downloadingRegion,
                    lat = location.latitude,
                    lon = location.longitude
                )
            when (resolution) {
                is ExploreRegionResolution.Active -> {
                    downloadController.stopObservation()
                    applyActiveRegions(selectRegionsForLocation(location))
                    _uiState.update {
                        it.copy(
                            regionResolution = resolution,
                            downloadPrompt = null,
                            downloadProgressPct = 0,
                            downloadUiMessage = null
                        )
                    }
                }
                is ExploreRegionResolution.Downloading -> {
                    applyActiveRegions(emptyList())
                    _uiState.update {
                        it.copy(
                            regionResolution = resolution,
                            downloadPrompt = null,
                            downloadProgressPct = resolution.region.downloadProgressPct,
                            downloadPromptDismissed = false
                        )
                    }
                    downloadController.observeProgress(resolution.region.id)
                }
                is ExploreRegionResolution.NeedsDownload -> {
                    applyActiveRegions(emptyList())
                    _uiState.update {
                        it.copy(
                            regionResolution = resolution,
                            downloadPrompt = resolution.proposal,
                            downloadProgressPct = 0,
                            downloadUiMessage = null
                        )
                    }
                    downloadController.stopObservation()
                }
            }
        } else {
            applyActiveRegions(selectRegionsForLocation(location))
            _uiState.update { it.copy(regionResolution = null, downloadPrompt = null) }
        }
        refreshGate()
    }

    private suspend fun selectRegionsForLocation(location: UserLocation): List<Region> = regionRepository
        .regionsContainingPoint(location.latitude, location.longitude)
        .filter { it.downloadStatus == DownloadStatus.READY || it.downloadStatus == DownloadStatus.PARTIAL }
        .sortedByDescending { it.updatedAt }

    private fun applyActiveRegions(regions: List<Region>) {
        val primary =
            regions.firstOrNull { it.id == requestedRegionId }
                ?: regions.maxByOrNull { it.updatedAt }
        visibilityPassScheduler.setActiveRegions(regions)
        _uiState.update {
            it.copy(
                activeRegion = primary,
                activeRegions = regions,
                activeRegionName = formatActiveRegionName(regions, primary),
                partialRegionWarning = regions.any { region -> region.downloadStatus == DownloadStatus.PARTIAL }
            )
        }
        locationRepository.lastLocation?.let { altitudeController.scheduleResolve(it, regions) }
        viewModelScope.launch(Dispatchers.IO) {
            regions.forEach { active ->
                runCatching { regionCoverageRepairUseCase.repairIfNeeded(active) }
            }
            refreshGate()
        }
    }

    private fun formatActiveRegionName(regions: List<Region>, primary: Region?): String? = when {
        regions.isEmpty() -> null
        regions.size == 1 -> regions.single().name
        else -> regions.joinToString(" · ") { it.name }.ifBlank { primary?.name }
    }

    private fun onOrientation(orientation: DeviceOrientation) {
        val compassBearingDeg = resolveCompassDisplayBearing(orientation)
        _uiState.update {
            it.copy(
                compassBearingDeg = compassBearingDeg,
                calibrationState = calibrationMonitor.calibrationState(orientation),
                debugSmoothedAzimuthDeg = orientation.trueAzimuthDeg
            )
        }
        refreshGate()
    }

    private fun resolveCompassDisplayBearing(orientation: DeviceOrientation): Float {
        if (abs(orientation.pitchDeg) <= COMPASS_DISPLAY_PITCH_LIMIT_DEG) {
            lastCompassBearingDeg = normalizeAzimuthDeg(orientation.trueAzimuthDeg)
        }
        return lastCompassBearingDeg
    }

    private fun normalizeAzimuthDeg(azimuthDeg: Float): Float {
        var normalized = azimuthDeg % 360f
        if (normalized < 0f) normalized += 360f
        return normalized
    }

    private fun onLocation(location: UserLocation) {
        val accuracyDegraded = ExploreLocationAccuracy.isDegraded(location.accuracyMeters)
        _uiState.update {
            it.copy(
                waitingForGpsFix = false,
                locationAccuracyMeters = location.accuracyMeters,
                locationAccuracyDegraded = accuracyDegraded,
                locationAccessState =
                if (it.locationAccessState == LocationAccessState.WAITING_FOR_FIX) {
                    LocationAccessState.GRANTED
                } else {
                    it.locationAccessState
                }
            )
        }
        reprojectLabels()

        viewModelScope.launch {
            val previousResolution = _uiState.value.regionResolution
            applyRegionResolution(location)
            val regions = _uiState.value.activeRegions
            if (previousResolution != _uiState.value.regionResolution &&
                _uiState.value.regionResolution is ExploreRegionResolution.NeedsDownload
            ) {
                _uiState.update { it.copy(downloadPromptDismissed = false) }
            }
            applyRegionBoundary(location, regions)
            refreshGate()
            altitudeController.scheduleResolve(location, regions)
        }
    }

    private fun applyRegionBoundary(location: UserLocation, regions: List<Region>) {
        val boundaryState = regionBoundaryController.onLocation(location, regions)
        if (boundaryState.insideActiveRegion) {
            regionBoundaryController.stopGraceTicker()
            _uiState.update {
                it.copy(
                    showRegionExitBanner = false,
                    showGraceExpiredDialog = false,
                    regionExitGraceSecondsRemaining = 0
                )
            }
            return
        }

        regionBoundaryController.startGraceTicker(viewModelScope) { graceState ->
            if (graceState.showGraceExpiredDialog && _uiState.value.simpleModeEnabled) {
                handleGraceExpiredSimpleMode(location)
                return@startGraceTicker
            }
            _uiState.update {
                it.copy(
                    showRegionExitBanner = graceState.showRegionExitBanner,
                    showGraceExpiredDialog = graceState.showGraceExpiredDialog,
                    regionExitGraceSecondsRemaining = graceState.regionExitGraceSecondsRemaining,
                    activeRegionName = formatActiveRegionName(regions, it.activeRegion) ?: it.activeRegionName
                )
            }
            refreshGate()
        }
    }

    private fun handleGraceExpiredSimpleMode(location: UserLocation) {
        regionBoundaryController.stopGraceTicker()
        visibilityPassScheduler.setActiveRegions(emptyList())
        _uiState.update {
            it.copy(
                activeRegion = null,
                activeRegions = emptyList(),
                activeRegionName = null,
                showRegionExitBanner = false,
                showGraceExpiredDialog = false,
                regionExitGraceSecondsRemaining = 0,
                downloadPromptDismissed = false
            )
        }
        viewModelScope.launch {
            applyRegionResolution(location)
            refreshGate()
        }
    }

    private fun collectOrientation() {
        viewModelScope.launch {
            orientationProvider.orientationFlow.collect { orientation ->
                hasReceivedOrientation = true
                currentOrientation = orientation
                onOrientation(orientation)
                reprojectLabels()
            }
        }
        viewModelScope.launch {
            unsmoothedOrientationProvider.orientationFlow.collect { orientation ->
                currentRawOrientation = orientation
                _uiState.update { state ->
                    state.copy(debugRawAzimuthDeg = orientation.trueAzimuthDeg)
                }
                if (_uiState.value.useRawSensorOverlay) {
                    reprojectLabels()
                }
            }
        }
    }

    private fun collectDebugPreferences() {
        viewModelScope.launch {
            userPreferencesRepository.showRawSensorOverlay.collect { useRaw ->
                _uiState.update { it.copy(useRawSensorOverlay = useRaw) }
                reprojectLabels()
            }
        }
    }

    private fun collectLocation() {
        viewModelScope.launch {
            locationRepository.locationFlow.collect { location -> onLocation(location) }
        }
    }

    private fun collectVisibility() {
        viewModelScope.launch {
            visibilityPassScheduler.visibleEntities.collect { entities ->
                cachedVisibleEntities = entities
                _uiState.update {
                    it.copy(
                        visibleEntityCount = entities.size,
                        showNoVisibleEntitiesHint =
                        entities.isEmpty() &&
                            it.exploreGate == ExploreGate.READY &&
                            it.screenWidthPx > 0f
                    )
                }
                reprojectLabels()
            }
        }
        viewModelScope.launch {
            visibilityPassScheduler.warnings.collect { warnings -> updatePartialWarning(warnings) }
        }
    }

    private fun updatePartialWarning(warnings: Set<VisibilityWarning>) {
        val regionPartial =
            _uiState.value.activeRegions.any { it.downloadStatus == DownloadStatus.PARTIAL }
        _uiState.update {
            it.copy(partialRegionWarning = regionPartial || VisibilityWarning.DEM_TILE_MISSING in warnings)
        }
    }

    private fun refreshGate() {
        val state = _uiState.value
        val regionDownloadNeeded = state.regionResolution is ExploreRegionResolution.NeedsDownload
        val gate =
            ExplorePreconditions.resolveGate(
                locationAccessState = state.locationAccessState,
                waitingForGpsFix = state.waitingForGpsFix,
                cameraGranted = state.cameraGranted,
                calibrationState = resolveCalibrationState(state.calibrationState),
                activeRegion = state.activeRegion,
                graceExpired = state.showGraceExpiredDialog,
                simpleModeEnabled = state.simpleModeEnabled,
                regionDownloadNeeded = regionDownloadNeeded,
                regionDownloading = state.regionResolution is ExploreRegionResolution.Downloading,
                downloadPromptDismissed = state.downloadPromptDismissed
            )
        _uiState.update {
            it.copy(
                exploreGate = gate,
                showNoVisibleEntitiesHint =
                cachedVisibleEntities.isEmpty() &&
                    gate == ExploreGate.READY &&
                    it.screenWidthPx > 0f
            )
        }
    }

    private fun resolveCalibrationState(state: CompassCalibrationState): CompassCalibrationState =
        if (!hasReceivedOrientation && state == CompassCalibrationState.UNAVAILABLE) {
            CompassCalibrationState.UNAVAILABLE
        } else {
            state
        }

    private fun reprojectLabels() {
        val state = _uiState.value
        val orientation = currentOrientation
        val canProject =
            orientation != null &&
                state.screenWidthPx > 0f &&
                state.screenHeightPx > 0f &&
                state.exploreGate == ExploreGate.READY &&
                !state.locationAccuracyDegraded

        if (!canProject) {
            _uiState.update { it.copy(clusteredLabels = emptyList(), arLabels = emptyList()) }
            return
        }

        val projectedOrientation =
            if (state.useRawSensorOverlay) {
                currentRawOrientation ?: orientation
            } else {
                orientation
            }

        val (clusters, labels) =
            ExploreLabelProjector.project(
                entities = cachedVisibleEntities,
                orientation = projectedOrientation,
                fov = state.cameraFov,
                screenWidthPx = state.screenWidthPx,
                screenHeightPx = state.screenHeightPx,
                expandedBucketIndex = state.expandedBucketIndex
            )

        _uiState.update {
            it.copy(
                clusteredLabels = clusters,
                arLabels = labels,
                expandedCluster =
                state.expandedBucketIndex?.let { bucket ->
                    clusters.firstOrNull { cluster -> cluster.bucketIndex == bucket }
                }
            )
        }
    }

    companion object {
        private const val EXPLORE_LOCATION_TOKEN = "explore"
        private const val EXPLORE_ORIENTATION_TOKEN = "explore"
        private const val COMPASS_DISPLAY_PITCH_LIMIT_DEG = 60f
    }
}
