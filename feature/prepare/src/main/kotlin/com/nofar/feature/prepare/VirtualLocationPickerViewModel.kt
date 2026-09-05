package com.nofar.feature.prepare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nofar.core.data.repository.RegionRepository
import com.nofar.core.location.LocationController
import com.nofar.core.location.LocationRepository
import com.nofar.core.model.LocationAccessState
import com.nofar.core.model.Region
import com.nofar.core.visibility.VirtualLocationMapPreviewUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VirtualLocationPickerUiState(
    val eligibleRegions: List<Region> = emptyList(),
    val selectedLat: Double? = null,
    val selectedLon: Double? = null,
    val selectionValid: Boolean = false,
    val helperMessage: String? = null,
    val mapRecenterNonce: Long = 0L,
    val loading: Boolean = true,
    val visibilityMask: MapVisibilityPreviewMask? = null,
    val analyzingVisibility: Boolean = false
)

@HiltViewModel
class VirtualLocationPickerViewModel
@Inject
constructor(
    private val regionRepository: RegionRepository,
    private val locationRepository: LocationRepository,
    private val locationController: LocationController,
    private val mapPreviewUseCase: VirtualLocationMapPreviewUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(VirtualLocationPickerUiState())
    val uiState: StateFlow<VirtualLocationPickerUiState> = _uiState.asStateFlow()
    private var previewJob: Job? = null

    init {
        locationController.acquire(PICKER_LOCATION_TOKEN)
        viewModelScope.launch {
            regionRepository.observeAllRegions().collect { regions ->
                val eligible = VirtualLocationSelectionLogic.exploreEligible(regions)
                val device = locationRepository.lastLocation
                val center =
                    VirtualLocationSelectionLogic.initialMapCenter(
                        regions = eligible,
                        deviceLat = device?.latitude,
                        deviceLon = device?.longitude
                    )
                _uiState.update { state ->
                    val lat = state.selectedLat ?: center?.first
                    val lon = state.selectedLon ?: center?.second
                    val selection =
                        if (lat != null && lon != null) {
                            VirtualLocationSelectionLogic.resolveSelection(eligible, lat, lon)
                        } else {
                            null
                        }
                    val validSelection = selection != null
                    state.copy(
                        eligibleRegions = eligible,
                        selectedLat = lat,
                        selectedLon = lon,
                        selectionValid = validSelection,
                        helperMessage =
                        VirtualLocationPickerMessages.helper(
                            eligibleRegions = eligible,
                            lat = lat,
                            lon = lon,
                            selectionValid = validSelection,
                            analyzingVisibility = state.analyzingVisibility
                        ),
                        loading = false,
                        mapRecenterNonce =
                        if (state.selectedLat == null && center != null) {
                            state.mapRecenterNonce + 1
                        } else {
                            state.mapRecenterNonce
                        }
                    )
                }
                val current = _uiState.value
                if (current.selectionValid && current.selectedLat != null && current.selectedLon != null) {
                    val sel =
                        VirtualLocationSelectionLogic.resolveSelection(
                            eligible,
                            current.selectedLat,
                            current.selectedLon
                        )
                    if (sel != null) {
                        refreshVisibilityPreview(sel)
                    }
                }
            }
        }
    }

    fun onLocationPermissionChanged(accessState: LocationAccessState) {
        if (accessState == LocationAccessState.GRANTED) {
            locationRepository.start()
        } else {
            locationRepository.onPermissionRevoked()
        }
    }

    fun onMapTap(lat: Double, lon: Double) {
        val eligible = _uiState.value.eligibleRegions
        if (eligible.isEmpty()) {
            _uiState.update {
                it.copy(
                    helperMessage = VirtualLocationPickerMessages.NO_DOWNLOADED_REGIONS
                )
            }
            return
        }
        val selection = VirtualLocationSelectionLogic.resolveSelection(eligible, lat, lon)
        if (selection == null) {
            _uiState.update {
                it.copy(
                    helperMessage = VirtualLocationPickerMessages.OUTSIDE_ACTIVE_REGION
                )
            }
            return
        }
        replaceVisibilityMask(null)
        _uiState.update {
            it.copy(
                selectedLat = lat,
                selectedLon = lon,
                selectionValid = true,
                visibilityMask = null,
                analyzingVisibility = true,
                helperMessage = VirtualLocationPickerMessages.ANALYZING_VISIBILITY
            )
        }
        refreshVisibilityPreview(selection)
    }

    fun currentSelection(): VirtualLocationSelection? {
        val state = _uiState.value
        val lat = state.selectedLat
        val lon = state.selectedLon
        return if (lat != null && lon != null) {
            VirtualLocationSelectionLogic.resolveSelection(state.eligibleRegions, lat, lon)
        } else {
            null
        }
    }

    private fun refreshVisibilityPreview(selection: VirtualLocationSelection) {
        previewJob?.cancel()
        previewJob =
            viewModelScope.launch(Dispatchers.Default) {
                var producedMask: MapVisibilityPreviewMask? = null
                try {
                    val state = _uiState.value
                    val contributingRegions =
                        state.eligibleRegions.filter { selection.contributingRegionIds.contains(it.id) }
                    val clipRegions =
                        state.eligibleRegions.filter { selection.contributingRegionIds.contains(it.id) }
                    val preview =
                        mapPreviewUseCase.compute(
                            regions = contributingRegions,
                            clipRegions = clipRegions,
                            observerLat = selection.lat,
                            observerLon = selection.lon
                        )
                    coroutineContext.ensureActive()
                    producedMask =
                        preview?.let { computed ->
                            val (bitmap, bounds) = MapVisibilityPreviewMaskRaster.rasterize(computed)
                            MapVisibilityPreviewMask(bitmap = bitmap, bounds = bounds)
                        }
                    coroutineContext.ensureActive()
                    val previousMask = _uiState.value.visibilityMask
                    _uiState.update { current ->
                        current.copy(
                            visibilityMask = producedMask,
                            analyzingVisibility = false,
                            helperMessage =
                            if (preview == null) {
                                VirtualLocationPickerMessages.ELEVATION_REFRESH_REQUIRED
                            } else {
                                VirtualLocationPickerMessages.helper(
                                    eligibleRegions = current.eligibleRegions,
                                    lat = current.selectedLat,
                                    lon = current.selectedLon,
                                    selectionValid = current.selectionValid,
                                    analyzingVisibility = false
                                )
                            }
                        )
                    }
                    producedMask = null
                    if (previousMask != null && previousMask !== _uiState.value.visibilityMask) {
                        previousMask.bitmap.recycle()
                    }
                } finally {
                    producedMask?.bitmap?.recycle()
                }
            }
    }

    private fun replaceVisibilityMask(mask: MapVisibilityPreviewMask?) {
        val previous = _uiState.value.visibilityMask
        _uiState.update { it.copy(visibilityMask = mask) }
        if (previous != null && previous !== mask) {
            previous.bitmap.recycle()
        }
    }

    override fun onCleared() {
        previewJob?.cancel()
        replaceVisibilityMask(null)
        locationController.release(PICKER_LOCATION_TOKEN)
        super.onCleared()
    }

    companion object {
        private const val PICKER_LOCATION_TOKEN = "virtual_location_picker"
    }
}

internal object VirtualLocationPickerMessages {
    const val NO_DOWNLOADED_REGIONS: String =
        "No downloaded regions yet. Open Prepare and download map data before exploring another location."

    const val OUTSIDE_ACTIVE_REGION: String =
        "That point is outside your downloaded regions. Pan to a green circle or use Prepare to download this area."

    const val ANALYZING_VISIBILITY: String = "Analyzing terrain visibility from this point…"

    const val ELEVATION_REFRESH_REQUIRED: String =
        "Elevation data needs refreshing. Open Prepare and download this region again."

    const val VALID_SELECTION_HINT: String =
        "Green = open view along the ground. Red = terrain blocks sight. Gray areas have no elevation data."

    const val TAP_HINT: String =
        "Tap inside a downloaded region (green circle). The overlay shows what terrain you could see from that point."

    fun helper(
        eligibleRegions: List<Region>,
        lat: Double?,
        lon: Double?,
        selectionValid: Boolean,
        analyzingVisibility: Boolean
    ): String? = when {
        eligibleRegions.isEmpty() -> NO_DOWNLOADED_REGIONS
        lat == null || lon == null -> TAP_HINT
        analyzingVisibility -> ANALYZING_VISIBILITY
        selectionValid -> VALID_SELECTION_HINT
        else -> OUTSIDE_ACTIVE_REGION
    }
}
