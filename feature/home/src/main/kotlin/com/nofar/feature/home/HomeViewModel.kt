package com.nofar.feature.home

import android.content.Context
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nofar.core.data.repository.RegionRepository
import com.nofar.core.data.repository.StorageRepository
import com.nofar.core.data.usecase.RegionDeletionUseCase
import com.nofar.core.designsystem.component.RegionCardState
import com.nofar.core.location.LocationController
import com.nofar.core.location.LocationRepository
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.LocationAccessState
import com.nofar.core.model.Region
import com.nofar.core.model.UserLocation
import com.nofar.core.sensors.DeclinationCorrector
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val regions: List<RegionCardState> = emptyList(),
    val demCacheBytes: Long = 0L,
    val entitiesDbBytes: Long = 0L,
    val freeSpaceBytes: Long = 0L,
    val deleteConfirmRegion: Region? = null,
    val overlappingRegionsDialog: List<Region>? = null,
    val navigateToExploreRegionId: UUID? = null,
    val locationAccessState: LocationAccessState = LocationAccessState.NOT_REQUESTED,
    val waitingForGpsFix: Boolean = false
)

@HiltViewModel
class HomeViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val regionRepository: RegionRepository,
    private val storageRepository: StorageRepository,
    private val regionDeletionUseCase: RegionDeletionUseCase,
    private val locationRepository: LocationRepository,
    private val locationController: LocationController,
    private val declinationCorrector: DeclinationCorrector
) : ViewModel() {
    private val currentLocation = MutableStateFlow<UserLocation?>(null)
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        locationController.acquire(HOME_LOCATION_TOKEN)
        viewModelScope.launch {
            combine(
                regionRepository.observeAllRegions(),
                currentLocation
            ) { regions, location ->
                buildHomeRegionCards(regionRepository, regions, location)
            }.collect { cards ->
                _uiState.update { it.copy(regions = cards) }
            }
        }
        viewModelScope.launch {
            locationRepository.locationFlow.collect { location ->
                currentLocation.value = location
                _uiState.update {
                    it.copy(
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
        }
        refreshStorageStats()
    }

    fun onLocationPermissionChanged(accessState: LocationAccessState) {
        if (accessState == LocationAccessState.GRANTED) {
            locationRepository.start()
        } else {
            currentLocation.value = null
            locationRepository.onPermissionRevoked()
            declinationCorrector.clearSeedLocation()
        }
        _uiState.update { state ->
            val waiting =
                accessState == LocationAccessState.GRANTED &&
                    currentLocation.value == null
            state.copy(
                locationAccessState = if (waiting) LocationAccessState.WAITING_FOR_FIX else accessState,
                waitingForGpsFix = waiting
            )
        }
    }

    fun refreshStorageStats() {
        viewModelScope.launch {
            val stats = storageRepository.getStorageStats()
            _uiState.update {
                it.copy(
                    demCacheBytes = stats.demCacheSizeBytes,
                    entitiesDbBytes = stats.entityDbSizeBytes,
                    freeSpaceBytes = readFreeSpaceBytes(context)
                )
            }
        }
    }

    fun onEnterExploreClicked(regionId: UUID) {
        viewModelScope.launch {
            val location = currentLocation.value
            val region = regionRepository.getRegion(regionId) ?: return@launch
            if (region.downloadStatus != DownloadStatus.READY && region.downloadStatus != DownloadStatus.PARTIAL) {
                return@launch
            }
            if (location == null) {
                _uiState.update { it.copy(navigateToExploreRegionId = regionId) }
                return@launch
            }
            val overlapping =
                regionRepository
                    .regionsContainingPoint(location.latitude, location.longitude)
                    .filter {
                        it.downloadStatus == DownloadStatus.READY || it.downloadStatus == DownloadStatus.PARTIAL
                    }
            if (overlapping.size > 1) {
                _uiState.update {
                    it.copy(overlappingRegionsDialog = overlapping)
                }
            } else {
                _uiState.update { it.copy(navigateToExploreRegionId = regionId) }
            }
        }
    }

    fun onOverlappingRegionSelected(regionId: UUID) {
        _uiState.update {
            it.copy(overlappingRegionsDialog = null, navigateToExploreRegionId = regionId)
        }
    }

    fun dismissOverlappingRegionsDialog() {
        _uiState.update { it.copy(overlappingRegionsDialog = null) }
    }

    fun onExploreNavigationHandled() {
        _uiState.update { it.copy(navigateToExploreRegionId = null) }
    }

    fun onDeleteClicked(regionId: UUID) {
        viewModelScope.launch {
            val region = regionRepository.getRegion(regionId) ?: return@launch
            _uiState.update { it.copy(deleteConfirmRegion = region) }
        }
    }

    fun confirmDeleteRegion() {
        val region = _uiState.value.deleteConfirmRegion ?: return
        viewModelScope.launch {
            regionDeletionUseCase.execute(region.id)
            _uiState.update { it.copy(deleteConfirmRegion = null) }
            refreshStorageStats()
        }
    }

    fun dismissDeleteRegion() {
        _uiState.update { it.copy(deleteConfirmRegion = null) }
    }

    override fun onCleared() {
        locationController.release(HOME_LOCATION_TOKEN)
        super.onCleared()
    }

    companion object {
        private const val HOME_LOCATION_TOKEN = "home"
    }
}

internal fun readFreeSpaceBytes(context: Context): Long = runCatching {
    val stat = StatFs(context.filesDir.path)
    stat.availableBlocksLong * stat.blockSizeLong
}.getOrDefault(0L)
