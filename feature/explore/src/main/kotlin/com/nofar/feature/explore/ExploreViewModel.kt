package com.nofar.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nofar.core.location.LocationController
import com.nofar.core.location.LocationRepository
import com.nofar.core.model.CompassCalibrationState
import com.nofar.core.model.DeviceOrientation
import com.nofar.core.model.LocationAccessState
import com.nofar.core.model.UserLocation
import com.nofar.core.sensors.CompassCalibrationMonitor
import com.nofar.core.sensors.CompassRibbonFormatter
import com.nofar.core.sensors.CompassRibbonLabels
import com.nofar.core.sensors.DeclinationCorrector
import com.nofar.core.sensors.OrientationController
import com.nofar.core.sensors.OrientationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val debugRawAzimuthDeg: Float? = null,
    val debugSmoothedAzimuthDeg: Float? = null
)

@HiltViewModel
class ExploreViewModel
@Inject
constructor(
    private val orientationProvider: OrientationProvider,
    private val orientationController: OrientationController,
    private val locationRepository: LocationRepository,
    private val locationController: LocationController,
    private val calibrationMonitor: CompassCalibrationMonitor,
    private val declinationCorrector: DeclinationCorrector
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    init {
        locationController.acquire(EXPLORE_LOCATION_TOKEN)
        orientationController.acquire(EXPLORE_ORIENTATION_TOKEN)
        viewModelScope.launch {
            orientationProvider.orientationFlow.collect { orientation ->
                onOrientation(orientation)
            }
        }
        viewModelScope.launch {
            locationRepository.locationFlow.collect { location ->
                onLocation(location)
            }
        }
    }

    fun onLocationPermissionChanged(accessState: LocationAccessState) {
        if (accessState == LocationAccessState.GRANTED) {
            locationRepository.start()
        } else {
            locationRepository.onPermissionRevoked()
            declinationCorrector.clearSeedLocation()
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
    }

    override fun onCleared() {
        locationController.release(EXPLORE_LOCATION_TOKEN)
        orientationController.release(EXPLORE_ORIENTATION_TOKEN)
        super.onCleared()
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
    }

    private fun onLocation(location: UserLocation) {
        val altitude =
            location.altitudeMeters?.let { alt ->
                alt.toInt().toString()
            }
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
    }

    companion object {
        private const val EXPLORE_LOCATION_TOKEN = "explore"
        private const val EXPLORE_ORIENTATION_TOKEN = "explore"
    }
}
