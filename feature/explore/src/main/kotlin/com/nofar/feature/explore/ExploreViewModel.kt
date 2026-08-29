@file:Suppress("TooManyFunctions", "LargeClass")

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
import com.nofar.core.data.usecase.InsideRegionUseCase
import com.nofar.core.data.usecase.QuickRegionDownloadUseCase
import com.nofar.core.data.usecase.QuickRegionProposal
import com.nofar.core.data.usecase.RegionCoverageRepairUseCase
import com.nofar.core.designsystem.component.HorizonOutlinePoint
import com.nofar.core.location.LocationController
import com.nofar.core.location.LocationRepository
import com.nofar.core.model.AppConfig
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
import com.nofar.core.visibility.HereContext
import com.nofar.core.visibility.HorizonAlignmentRejectReason
import com.nofar.core.visibility.HorizonAlignmentResult
import com.nofar.core.visibility.HorizonProfile
import com.nofar.core.visibility.HorizonProjector
import com.nofar.core.visibility.VisibilityPassScheduler
import com.nofar.core.visibility.VisibilityWarning
import com.nofar.core.visibility.VisibleEntity
import com.nofar.core.visibility.excludingHereContext
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
    private val insideRegionUseCase: InsideRegionUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val quickRegionDownloadUseCase: QuickRegionDownloadUseCase,
    private val downloadScheduler: PrepareDownloadScheduler,
    private val networkConnectivityMonitor: NetworkConnectivityMonitor,
    private val horizonAlignmentEngine: ExploreHorizonAlignmentEngine,
    private val cameraFrameStore: ExploreCameraFrameStore
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    private val virtualExploreSession: VirtualExploreSession? =
        ExploreRouteBuilder.parseVirtualSession(
            regionIdRaw = savedStateHandle.get<String>("regionId"),
            virtualLatRaw = savedStateHandle.get<String>("virtualLat"),
            virtualLonRaw = savedStateHandle.get<String>("virtualLon")
        )

    private val requestedRegionId: UUID? =
        virtualExploreSession?.primaryRegionId
            ?: ExploreRouteBuilder.parseRegionId(savedStateHandle.get<String>("regionId"))

    private val virtualObserverLocation: UserLocation? =
        virtualExploreSession?.let(::virtualObserverLocation)

    private val regionBoundaryController = ExploreRegionBoundaryController()
    private val altitudeController =
        ExploreAltitudeController(
            scope = viewModelScope,
            displayAltitudeResolver = displayAltitudeResolver,
            uiState = _uiState,
            activeRegion = { _uiState.value.activeRegion },
            isVirtual = virtualExploreSession != null
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
    private val autoDownloadGuard = ExploreAutoDownloadGuard()
    private var cachedVisibleEntities: List<VisibleEntity> = emptyList()
    private var cachedHereContext: HereContext = HereContext()
    private var cachedHorizonProfile: HorizonProfile? = null
    private var currentOrientation: DeviceOrientation? = null
    private var currentRawOrientation: DeviceOrientation? = null
    private var hasReceivedOrientation: Boolean = false
    private var lastCompassBearingDeg: Float = 0f
    private val horizonAlignmentScheduler = ExploreHorizonAlignmentScheduler()
    private var horizonAlignmentInProgress = false
    private var horizonAlignmentWarningShownThisSession = false

    val exploreCameraFrameStore: ExploreCameraFrameStore = cameraFrameStore

    init {
        configureObserverLocationSource()
        locationController.acquire(EXPLORE_LOCATION_TOKEN)
        orientationController.acquire(EXPLORE_ORIENTATION_TOKEN)
        visibilityPassScheduler.start(viewModelScope)
        virtualObserverLocation?.let(visibilityPassScheduler::seedObserverLocation)
        _uiState.update {
            it.copy(
                virtualExploreSession = virtualExploreSession,
                waitingForGpsFix = if (virtualExploreSession != null) false else it.waitingForGpsFix
            )
        }
        collectSimpleModePreference()
        collectHorizonOutlinePreference()
        collectHorizonAlignmentOffsets()
        collectLabelElevationPreference()
        viewModelScope.launch { resolveInitialRegion() }
        collectOrientation()
        collectLocation()
        collectVisibility()
        collectDebugPreferences()
    }

    private fun configureObserverLocationSource() {
        val observerFlow =
            virtualObserverLocation?.let { location -> flowOf(location) }
                ?: locationRepository.locationFlow
        visibilityPassScheduler.configureObserverLocation(
            observerFlow = observerFlow,
            periodicRefresh = virtualObserverLocation != null
        )
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
            val waitingForDeviceFix =
                virtualObserverLocation == null &&
                    accessState == LocationAccessState.GRANTED &&
                    locationRepository.lastLocation == null
            state.copy(
                altitude = if (accessState == LocationAccessState.GRANTED) state.altitude else null,
                locationAccessState =
                if (waitingForDeviceFix) LocationAccessState.WAITING_FOR_FIX else accessState,
                waitingForGpsFix = waitingForDeviceFix
            )
        }
        if (accessState == LocationAccessState.GRANTED) {
            virtualObserverLocation?.let { observer ->
                altitudeController.scheduleResolve(observer, _uiState.value.activeRegions)
            } ?: locationRepository.lastLocation?.let { location ->
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
        reprojectOverlay()
    }

    fun onCameraFieldOfViewChanged(fov: CameraFieldOfView) {
        _uiState.update { it.copy(cameraBaseFov = fov).withZoomAdjustedFov() }
        reprojectOverlay()
    }

    fun onCameraZoomRangeChanged(minZoomRatio: Float, maxZoomRatio: Float) {
        val cappedMax = minOf(maxZoomRatio, AppConfig.EXPLORE_MAX_ZOOM_RATIO)
        _uiState.update { state ->
            val clampedRatio = state.zoomRatio.coerceIn(minZoomRatio, cappedMax)
            state.copy(
                minZoomRatio = minZoomRatio,
                maxZoomRatio = cappedMax,
                zoomRatio = clampedRatio
            ).withZoomAdjustedFov()
        }
        reprojectOverlay()
    }

    fun onZoomGesture(scaleFactor: Float) {
        if (scaleFactor == 1f) return
        _uiState.update { state ->
            if (state.maxZoomRatio <= state.minZoomRatio) return@update state
            val newRatio = clampZoom(
                current = state.zoomRatio,
                scaleFactor = scaleFactor,
                min = state.minZoomRatio,
                max = state.maxZoomRatio
            )
            if (newRatio == state.zoomRatio) return@update state
            state.copy(zoomRatio = newRatio).withZoomAdjustedFov()
        }
        reprojectOverlay()
    }

    fun onZoomStep(direction: ZoomStepDirection) {
        _uiState.update { state ->
            if (state.maxZoomRatio <= state.minZoomRatio) return@update state
            val step = AppConfig.EXPLORE_ZOOM_BUTTON_STEP
            val newRatio =
                when (direction) {
                    ZoomStepDirection.IN ->
                        (state.zoomRatio * step).coerceAtMost(state.maxZoomRatio)
                    ZoomStepDirection.OUT ->
                        (state.zoomRatio / step).coerceAtLeast(state.minZoomRatio)
                }
            if (newRatio == state.zoomRatio) return@update state
            state.copy(zoomRatio = newRatio).withZoomAdjustedFov()
        }
        reprojectOverlay()
    }

    fun onZoomReset() {
        _uiState.update { state ->
            if (state.zoomRatio == state.minZoomRatio) return@update state
            state.copy(zoomRatio = state.minZoomRatio).withZoomAdjustedFov()
        }
        reprojectOverlay()
    }

    fun onHiddenCountClicked(bucketIndex: Int) {
        _uiState.update { it.copy(expandedBucketIndex = bucketIndex) }
        reprojectOverlay()
    }

    fun onDismissExpandedBucket() {
        _uiState.update { it.copy(expandedBucketIndex = null, expandedCluster = null) }
        reprojectOverlay()
    }

    fun onDownloadRetry() {
        val proposal = _uiState.value.downloadPrompt ?: return
        autoDownloadGuard.clearForRetry(proposal)
        viewModelScope.launch { startDownloadWithPolicy(proposal, forceRetry = true) }
    }

    fun confirmCellularDownload() {
        val proposal = downloadController.pendingCellularProposal ?: _uiState.value.downloadPrompt ?: return
        downloadController.pendingCellularProposal = null
        _uiState.update { it.copy(showCellularWarning = false) }
        viewModelScope.launch {
            autoDownloadGuard.clearOnSuccess(proposal)
            downloadController.startDownload(proposal)
        }
    }

    fun dismissCellularWarning() {
        val proposal = downloadController.pendingCellularProposal ?: _uiState.value.downloadPrompt
        downloadController.pendingCellularProposal = null
        proposal?.let { autoDownloadGuard.markCellularDeclined(it) }
        _uiState.update { it.copy(showCellularWarning = false) }
    }

    fun dismissWifiOnlyBlocked() {
        _uiState.update { it.copy(showWifiOnlyBlocked = false) }
    }

    private fun maybeAutoStartDownload(proposal: QuickRegionProposal) {
        val state = _uiState.value
        val canAutoStart =
            state.simpleModeEnabled &&
                state.regionResolution !is ExploreRegionResolution.Downloading &&
                !state.showCellularWarning &&
                autoDownloadGuard.shouldAttempt(proposal, forceRetry = false)
        if (canAutoStart) {
            viewModelScope.launch { startDownloadWithPolicy(proposal) }
        }
    }

    private suspend fun startDownloadWithPolicy(proposal: QuickRegionProposal, forceRetry: Boolean = false) {
        if (!forceRetry && !autoDownloadGuard.shouldAttempt(proposal, forceRetry = false)) return
        if (!networkConnectivityMonitor.isNetworkAvailable()) {
            autoDownloadGuard.markAttempted(proposal)
            _uiState.update {
                it.copy(downloadUiMessage = "No network connection. Connect to Wi-Fi or mobile data to download.")
            }
            return
        }
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
                autoDownloadGuard.markAttempted(proposal)
                if (wifiOnly && onCellular) {
                    _uiState.update { it.copy(showWifiOnlyBlocked = true) }
                } else {
                    _uiState.update { it.copy(downloadUiMessage = gate.message) }
                }
            }
            DownloadPolicy.GateResult.CellularWarning -> {
                autoDownloadGuard.markAttempted(proposal)
                downloadController.pendingCellularProposal = proposal
                _uiState.update { it.copy(showCellularWarning = true) }
            }
            DownloadPolicy.GateResult.Proceed -> {
                autoDownloadGuard.clearOnSuccess(proposal)
                downloadController.startDownload(proposal)
            }
        }
    }

    override fun onCleared() {
        downloadController.onCleared()
        regionBoundaryController.stopGraceTicker()
        visibilityPassScheduler.stop()
        locationController.release(EXPLORE_LOCATION_TOKEN)
        orientationController.release(EXPLORE_ORIENTATION_TOKEN)
        super.onCleared()
    }

    fun onDismissHorizonAlignmentWarning() {
        _uiState.update { it.copy(showHorizonAlignmentWarning = false) }
    }

    private fun collectHorizonAlignmentOffsets() {
        viewModelScope.launch {
            userPreferencesRepository.horizonAzimuthOffsetDeg.collect { azimuthOffset ->
                _uiState.update { it.copy(horizonAzimuthOffsetDeg = azimuthOffset) }
                reprojectOverlay()
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.horizonPitchOffsetDeg.collect { pitchOffset ->
                _uiState.update { it.copy(horizonPitchOffsetDeg = pitchOffset) }
                reprojectOverlay()
            }
        }
    }

    private fun collectHorizonOutlinePreference() {
        viewModelScope.launch {
            userPreferencesRepository.showHorizonOutline.collect { enabled ->
                _uiState.update { it.copy(showHorizonOutline = enabled) }
                // Preference gates the sweep inside the visibility pass — reproject alone is not enough.
                visibilityPassScheduler.requestPass(force = true)
                reprojectOverlay()
            }
        }
    }

    private fun collectSimpleModePreference() {
        viewModelScope.launch {
            userPreferencesRepository.simpleModeEnabled.collect { enabled ->
                _uiState.update { it.copy(simpleModeEnabled = enabled) }
                refreshGate()
            }
        }
    }

    private fun collectLabelElevationPreference() {
        viewModelScope.launch {
            userPreferencesRepository.showLabelElevation.collect { enabled ->
                _uiState.update { it.copy(showLabelElevation = enabled) }
                reprojectOverlay()
            }
        }
    }

    private suspend fun resolveInitialRegion() {
        virtualObserverLocation?.let { observer ->
            resolveVirtualExplore(observer)
            return
        }
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

    private suspend fun resolveVirtualExplore(observer: UserLocation) {
        val session = virtualExploreSession ?: return
        val eligible =
            insideRegionUseCase.exploreEligibleRegionsContainingPoint(
                observer.latitude,
                observer.longitude
            )
        if (eligible.none { it.id == session.primaryRegionId }) {
            applyActiveRegions(emptyList())
            refreshGate()
            return
        }
        applyRegionResolution(observer)
        altitudeController.scheduleResolve(observer, _uiState.value.activeRegions)
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
                            downloadProgressPct = resolution.region.downloadProgressPct
                        )
                    }
                    downloadController.observeProgress(resolution.region.id)
                }
                is ExploreRegionResolution.NeedsDownload -> {
                    applyActiveRegions(emptyList())
                    autoDownloadGuard.onProposalChanged(resolution.proposal)
                    _uiState.update {
                        it.copy(
                            regionResolution = resolution,
                            downloadPrompt = resolution.proposal,
                            downloadProgressPct = 0,
                            downloadUiMessage = null
                        )
                    }
                    downloadController.stopObservation()
                    maybeAutoStartDownload(resolution.proposal)
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
        virtualObserverLocation?.let { observer ->
            altitudeController.scheduleResolve(observer, regions)
        } ?: locationRepository.lastLocation?.let { altitudeController.scheduleResolve(it, regions) }
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
        val alignedOrientation = orientationWithStoredOffsets(orientation, _uiState.value)
        val compassBearingDeg = resolveCompassDisplayBearing(alignedOrientation)
        _uiState.update {
            it.copy(
                compassBearingDeg = compassBearingDeg,
                calibrationState = calibrationMonitor.calibrationState(orientation),
                debugSmoothedAzimuthDeg = orientation.trueAzimuthDeg
            )
        }
        maybeAttemptHorizonAlignment(orientation)
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
        val isVirtual = virtualObserverLocation != null
        val accuracyDegraded =
            if (isVirtual) {
                false
            } else {
                ExploreLocationAccuracy.isDegraded(location.accuracyMeters)
            }
        _uiState.update {
            it.copy(
                waitingForGpsFix = false,
                locationAccuracyMeters = if (isVirtual) null else location.accuracyMeters,
                locationAccuracyDegraded = accuracyDegraded,
                locationAccessState =
                if (it.locationAccessState == LocationAccessState.WAITING_FOR_FIX) {
                    LocationAccessState.GRANTED
                } else {
                    it.locationAccessState
                }
            )
        }
        reprojectOverlay()

        if (isVirtual) {
            refreshGate()
            return
        }

        viewModelScope.launch {
            applyRegionResolution(location)
            applyRegionBoundary(location, _uiState.value.activeRegions)
            refreshGate()
            altitudeController.scheduleResolve(location, _uiState.value.activeRegions)
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
                regionExitGraceSecondsRemaining = 0
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
                reprojectOverlay()
            }
        }
        viewModelScope.launch {
            unsmoothedOrientationProvider.orientationFlow.collect { orientation ->
                currentRawOrientation = orientation
                _uiState.update { state ->
                    state.copy(debugRawAzimuthDeg = orientation.trueAzimuthDeg)
                }
                if (_uiState.value.useRawSensorOverlay) {
                    reprojectOverlay()
                }
            }
        }
    }

    private fun collectDebugPreferences() {
        viewModelScope.launch {
            userPreferencesRepository.showRawSensorOverlay.collect { useRaw ->
                _uiState.update { it.copy(useRawSensorOverlay = useRaw) }
                reprojectOverlay()
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
                reprojectOverlay()
            }
        }
        viewModelScope.launch {
            visibilityPassScheduler.hereContext.collect { hereContext ->
                cachedHereContext = hereContext
                reprojectOverlay()
            }
        }
        viewModelScope.launch {
            visibilityPassScheduler.horizonProfile.collect { profile ->
                cachedHorizonProfile = profile
                _uiState.update { it.copy(horizonMeanAngleDeg = profile?.meanElevationAngleDeg()) }
                reprojectOverlay()
            }
        }
        viewModelScope.launch {
            visibilityPassScheduler.horizonEyeSource.collect { source ->
                _uiState.update { it.copy(horizonEyeSource = source?.name?.lowercase()) }
            }
        }
        viewModelScope.launch {
            visibilityPassScheduler.warnings.collect { warnings -> updatePartialWarning(warnings) }
        }
    }

    private fun HorizonProfile.meanElevationAngleDeg(): Float? =
        elevationAnglesDeg.takeIf { it.isNotEmpty() }?.average()?.toFloat()

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
                regionDownloading = state.regionResolution is ExploreRegionResolution.Downloading
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

    private fun reprojectOverlay() {
        val state = _uiState.value
        val orientation = currentOrientation
        val canProject =
            ExplorePreconditions.canProjectOverlay(
                hasOrientation = orientation != null,
                screenWidthPx = state.screenWidthPx,
                screenHeightPx = state.screenHeightPx,
                gate = state.exploreGate,
                locationAccuracyDegraded = state.locationAccuracyDegraded
            )

        if (!canProject || orientation == null) {
            _uiState.update {
                it.copy(
                    clusteredLabels = emptyList(),
                    arLabels = emptyList(),
                    horizonLineSegments = emptyList(),
                    horizonSegmentCount = 0,
                    debugCameraElevationDeg = null,
                    exploreHere = ExploreHereUi()
                )
            }
            return
        }

        val projectedOrientation = resolveProjectedOrientation(state, orientation)
        val horizonEntities = cachedVisibleEntities.excludingHereContext(cachedHereContext)
        val (clusters, labels) =
            ExploreLabelProjector.project(
                entities = horizonEntities,
                orientation = projectedOrientation,
                fov = state.cameraFov,
                screenWidthPx = state.screenWidthPx,
                screenHeightPx = state.screenHeightPx,
                showElevation = state.showLabelElevation,
                expandedBucketIndex = state.expandedBucketIndex
            )
        val horizonSegments = projectHorizonLineSegments(state, projectedOrientation)

        _uiState.update {
            it.copy(
                clusteredLabels = clusters,
                arLabels = labels,
                horizonLineSegments = horizonSegments,
                horizonSegmentCount = horizonSegments.size,
                debugCameraElevationDeg = projectedOrientation.cameraElevationDeg,
                exploreHere = exploreHereUiFrom(cachedHereContext),
                expandedCluster =
                state.expandedBucketIndex?.let { bucket ->
                    clusters.firstOrNull { cluster -> cluster.bucketIndex == bucket }
                }
            )
        }
    }

    private fun resolveProjectedOrientation(state: ExploreUiState, orientation: DeviceOrientation): DeviceOrientation {
        val baseOrientation =
            if (state.useRawSensorOverlay) {
                currentRawOrientation ?: orientation
            } else {
                orientation
            }
        return orientationWithStoredOffsets(baseOrientation, state)
    }

    private fun orientationWithStoredOffsets(orientation: DeviceOrientation, state: ExploreUiState): DeviceOrientation =
        orientation.copy(
            trueAzimuthDeg = normalizeAzimuthDeg(orientation.trueAzimuthDeg + state.horizonAzimuthOffsetDeg),
            cameraElevationDeg = orientation.cameraElevationDeg + state.horizonPitchOffsetDeg
        )

    private fun canAttemptHorizonAlignment(state: ExploreUiState): Boolean {
        val exploreReady =
            state.exploreGate == ExploreGate.READY &&
                state.cameraGranted &&
                state.showHorizonOutline
        return exploreReady && !horizonAlignmentInProgress && cachedHorizonProfile != null
    }

    private fun maybeAttemptHorizonAlignment(orientation: DeviceOrientation) {
        if (!canAttemptHorizonAlignment(_uiState.value)) return
        if (horizonAlignmentScheduler.onOrientation(orientation) != HorizonAlignmentAttemptGate.AttemptNow) return

        horizonAlignmentInProgress = true
        viewModelScope.launch(Dispatchers.Default) {
            val attemptState = _uiState.value
            val outcome =
                horizonAlignmentEngine.attemptAlignment(
                    orientation = orientation,
                    horizonProfile = cachedHorizonProfile!!,
                    fov = attemptState.cameraFov,
                    screenWidthPx = attemptState.screenWidthPx,
                    screenHeightPx = attemptState.screenHeightPx
                )
            horizonAlignmentInProgress = false
            if (
                !outcome.offsetsApplied &&
                !horizonAlignmentWarningShownThisSession &&
                shouldWarnAboutSkippedAlignment(outcome.result)
            ) {
                horizonAlignmentWarningShownThisSession = true
                _uiState.update { it.copy(showHorizonAlignmentWarning = true) }
            }
        }
    }

    private fun shouldWarnAboutSkippedAlignment(result: HorizonAlignmentResult): Boolean =
        result.rejectReason == HorizonAlignmentRejectReason.OVER_THRESHOLD ||
            result.rejectReason == HorizonAlignmentRejectReason.LOW_CONFIDENCE

    private fun projectHorizonLineSegments(
        state: ExploreUiState,
        orientation: DeviceOrientation
    ): List<List<HorizonOutlinePoint>> {
        val profile = cachedHorizonProfile
        if (!state.showHorizonOutline || profile == null) return emptyList()
        return HorizonProjector.project(
            profile = profile,
            trueAzimuthDeg = orientation.trueAzimuthDeg,
            cameraElevationDeg = orientation.cameraElevationDeg,
            fov = state.cameraFov,
            screenWidthPx = state.screenWidthPx,
            screenHeightPx = state.screenHeightPx
        ).map { polyline -> polyline.points.map { point -> HorizonOutlinePoint(point.xPx, point.yPx) } }
    }

    private fun exploreHereUiFrom(hereContext: HereContext): ExploreHereUi = ExploreHereUi(
        placeName = hereContext.place?.name,
        peakName = hereContext.peak?.name,
        peakElevationM = hereContext.peak?.elevation
    )

    companion object {
        private const val EXPLORE_LOCATION_TOKEN = "explore"
        private const val EXPLORE_ORIENTATION_TOKEN = "explore"
        private const val COMPASS_DISPLAY_PITCH_LIMIT_DEG = 60f
    }
}

private fun ExploreUiState.withZoomAdjustedFov(): ExploreUiState = copy(cameraFov = cameraBaseFov.zoomed(zoomRatio))
