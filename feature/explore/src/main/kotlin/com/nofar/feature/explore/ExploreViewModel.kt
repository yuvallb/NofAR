@file:Suppress("TooManyFunctions")

package com.nofar.feature.explore

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nofar.core.data.repository.RegionRepository
import com.nofar.core.location.LocationController
import com.nofar.core.location.LocationRepository
import com.nofar.core.model.CompassCalibrationState
import com.nofar.core.model.DeviceOrientation
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.LocationAccessState
import com.nofar.core.model.Region
import com.nofar.core.model.UserLocation
import com.nofar.core.sensors.CompassCalibrationMonitor
import com.nofar.core.sensors.CompassRibbonFormatter
import com.nofar.core.sensors.CompassRibbonLabels
import com.nofar.core.sensors.DeclinationCorrector
import com.nofar.core.sensors.OrientationController
import com.nofar.core.sensors.OrientationProvider
import com.nofar.core.sensors.di.UnsmoothedOrientation
import com.nofar.core.visibility.CameraFieldOfView
import com.nofar.core.visibility.VisibilityPassScheduler
import com.nofar.core.visibility.VisibilityWarning
import com.nofar.core.visibility.VisibleEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExploreUiState(
    val compassRibbon: CompassRibbonLabels = CompassRibbonFormatter.fromAzimuth(0f),
    val altitudeM: String? = null,
    val calibrationState: CompassCalibrationState = CompassCalibrationState.UNAVAILABLE,
    val locationAccessState: LocationAccessState = LocationAccessState.NOT_REQUESTED,
    val waitingForGpsFix: Boolean = false,
    val cameraGranted: Boolean = false,
    val exploreGate: ExploreGate = ExploreGate.WAITING_GPS,
    val activeRegion: Region? = null,
    val activeRegionName: String? = null,
    val partialRegionWarning: Boolean = false,
    val clusteredLabels: List<com.nofar.core.visibility.ClusteredLabel> = emptyList(),
    val arLabels: List<com.nofar.core.designsystem.component.ArLabel> = emptyList(),
    val expandedBucketIndex: Int? = null,
    val expandedCluster: com.nofar.core.visibility.ClusteredLabel? = null,
    val showRegionExitBanner: Boolean = false,
    val regionExitGraceSecondsRemaining: Int = 0,
    val showGraceExpiredDialog: Boolean = false,
    val showNoVisibleEntitiesHint: Boolean = false,
    val cameraFov: CameraFieldOfView = CameraFieldOfView.fallback(),
    val screenWidthPx: Float = 0f,
    val screenHeightPx: Float = 0f,
    val debugRawAzimuthDeg: Float? = null,
    val debugSmoothedAzimuthDeg: Float? = null,
    val visibleEntityCount: Int = 0
)

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
    private val regionRepository: RegionRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    private val requestedRegionId: UUID? =
        savedStateHandle.get<String>("regionId")?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private val regionBoundaryController = ExploreRegionBoundaryController()
    private var cachedVisibleEntities: List<VisibleEntity> = emptyList()
    private var currentOrientation: DeviceOrientation? = null
    private var hasReceivedOrientation: Boolean = false

    init {
        locationController.acquire(EXPLORE_LOCATION_TOKEN)
        orientationController.acquire(EXPLORE_ORIENTATION_TOKEN)
        visibilityPassScheduler.start(viewModelScope)
        viewModelScope.launch { resolveInitialRegion() }
        collectOrientation()
        collectLocation()
        collectVisibility()
    }

    fun onLocationPermissionChanged(accessState: LocationAccessState) {
        if (accessState == LocationAccessState.GRANTED) {
            locationRepository.start()
        } else {
            locationRepository.onPermissionRevoked()
            declinationCorrector.clearSeedLocation()
            visibilityPassScheduler.setActiveRegion(null)
        }
        _uiState.update { state ->
            val waiting =
                accessState == LocationAccessState.GRANTED &&
                    locationRepository.lastLocation == null
            state.copy(
                altitudeM = if (accessState == LocationAccessState.GRANTED) state.altitudeM else null,
                locationAccessState = if (waiting) LocationAccessState.WAITING_FOR_FIX else accessState,
                waitingForGpsFix = waiting
            )
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

    override fun onCleared() {
        regionBoundaryController.stopGraceTicker()
        visibilityPassScheduler.stop()
        locationController.release(EXPLORE_LOCATION_TOKEN)
        orientationController.release(EXPLORE_ORIENTATION_TOKEN)
        super.onCleared()
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
                _uiState.update { it.copy(debugRawAzimuthDeg = orientation.trueAzimuthDeg) }
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

    private suspend fun resolveInitialRegion() {
        val region =
            requestedRegionId?.let { regionRepository.getRegion(it) }
                ?: locationRepository.lastLocation?.let { selectRegionForLocation(it) }
        applyActiveRegion(region)
    }

    private suspend fun selectRegionForLocation(location: UserLocation): Region? = regionRepository
        .regionsContainingPoint(location.latitude, location.longitude)
        .filter { it.downloadStatus == DownloadStatus.READY || it.downloadStatus == DownloadStatus.PARTIAL }
        .maxByOrNull { it.updatedAt }

    private fun applyActiveRegion(region: Region?) {
        visibilityPassScheduler.setActiveRegion(region)
        _uiState.update {
            it.copy(
                activeRegion = region,
                activeRegionName = region?.name,
                partialRegionWarning = region?.downloadStatus == DownloadStatus.PARTIAL
            )
        }
        refreshGate()
    }

    private fun onOrientation(orientation: DeviceOrientation) {
        val ribbon = CompassRibbonFormatter.fromAzimuth(orientation.trueAzimuthDeg)
        _uiState.update {
            it.copy(
                compassRibbon = ribbon,
                calibrationState = calibrationMonitor.calibrationState(orientation),
                debugSmoothedAzimuthDeg = orientation.trueAzimuthDeg
            )
        }
        refreshGate()
    }

    private fun onLocation(location: UserLocation) {
        val altitude = location.altitudeMeters?.let { alt -> alt.toInt().toString() }
        _uiState.update {
            it.copy(
                altitudeM = altitude,
                waitingForGpsFix = false,
                locationAccessState =
                if (it.locationAccessState == LocationAccessState.WAITING_FOR_FIX) {
                    LocationAccessState.GRANTED
                } else {
                    it.locationAccessState
                }
            )
        }

        viewModelScope.launch {
            val region =
                requestedRegionId?.let { regionRepository.getRegion(it) }
                    ?: selectRegionForLocation(location)
            if (region?.id != _uiState.value.activeRegion?.id) {
                applyActiveRegion(region)
            }
            applyRegionBoundary(location, region)
            refreshGate()
        }
    }

    private fun applyRegionBoundary(location: UserLocation, region: Region?) {
        val boundaryState = regionBoundaryController.onLocation(location, region)
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
            _uiState.update {
                it.copy(
                    showRegionExitBanner = graceState.showRegionExitBanner,
                    showGraceExpiredDialog = graceState.showGraceExpiredDialog,
                    regionExitGraceSecondsRemaining = graceState.regionExitGraceSecondsRemaining,
                    activeRegionName = region?.name ?: it.activeRegionName
                )
            }
            refreshGate()
        }
    }

    private fun updatePartialWarning(warnings: Set<VisibilityWarning>) {
        val regionPartial = _uiState.value.activeRegion?.downloadStatus == DownloadStatus.PARTIAL
        _uiState.update {
            it.copy(partialRegionWarning = regionPartial || VisibilityWarning.DEM_TILE_MISSING in warnings)
        }
    }

    private fun refreshGate() {
        val state = _uiState.value
        val gate =
            ExplorePreconditions.resolveGate(
                locationAccessState = state.locationAccessState,
                waitingForGpsFix = state.waitingForGpsFix,
                cameraGranted = state.cameraGranted,
                calibrationState = resolveCalibrationState(state.calibrationState),
                activeRegion = state.activeRegion,
                graceExpired = state.showGraceExpiredDialog
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
                state.exploreGate == ExploreGate.READY

        if (!canProject) {
            _uiState.update { it.copy(clusteredLabels = emptyList(), arLabels = emptyList()) }
            return
        }

        val projectedOrientation = orientation

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
    }
}
