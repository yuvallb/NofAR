package com.nofar.feature.home

import android.content.Context
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nofar.core.data.repository.HomeRegionMetadataRepository
import com.nofar.core.data.repository.RegionRepository
import com.nofar.core.data.repository.StorageRepository
import com.nofar.core.data.usecase.InsideRegionUseCase
import com.nofar.core.data.usecase.RegionCoverageRepairUseCase
import com.nofar.core.data.usecase.RegionDeletionUseCase
import com.nofar.core.location.LocationController
import com.nofar.core.location.LocationRepository
import com.nofar.core.model.LocationAccessState
import com.nofar.core.model.Region
import com.nofar.core.model.UserLocation
import com.nofar.core.sensors.DeclinationCorrector
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val regions: List<com.nofar.core.designsystem.component.RegionCardState> = emptyList(),
    val insideRegionIds: Set<UUID> = emptySet(),
    val enterExploreEnabled: Boolean = false,
    val demCacheBytes: Long = 0L,
    val entitiesDbBytes: Long = 0L,
    val freeSpaceBytes: Long = 0L,
    val deleteConfirmRegion: Region? = null,
    val navigateToExploreRegionId: UUID? = null,
    val snackbarMessage: String? = null,
    val locationAccessState: LocationAccessState = LocationAccessState.NOT_REQUESTED,
    val waitingForGpsFix: Boolean = false,
    val loading: Boolean = true
)

@HiltViewModel
class HomeViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val regionRepository: RegionRepository,
    private val storageRepository: StorageRepository,
    private val regionDeletionUseCase: RegionDeletionUseCase,
    private val insideRegionUseCase: InsideRegionUseCase,
    private val metadataRepository: HomeRegionMetadataRepository,
    private val regionCoverageRepairUseCase: RegionCoverageRepairUseCase,
    private val locationRepository: LocationRepository,
    private val locationController: LocationController,
    private val declinationCorrector: DeclinationCorrector
) : ViewModel() {
    private val currentLocation = MutableStateFlow<UserLocation?>(null)
    private val insideExploreRegions = MutableStateFlow<List<Region>>(emptyList())
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private val exploreNavigation = HomeExploreNavigation(_uiState)

    init {
        seedCachedLocation()
        locationController.acquire(HOME_LOCATION_TOKEN)
        viewModelScope.launch {
            combine(
                regionRepository.observeAllRegions(),
                currentLocation
            ) { regions, location -> regions to location }
                .flowOn(Dispatchers.IO)
                .mapLatest { (regions, location) ->
                    regions.forEach { region ->
                        runCatching { regionCoverageRepairUseCase.repairIfNeeded(region) }
                    }
                    val insideExplore = HomeRegionLogic.exploreEligibleInside(regions, location)
                    insideExploreRegions.value = insideExplore
                    val cards =
                        buildHomeRegionCards(
                            insideRegionUseCase = insideRegionUseCase,
                            metadataRepository = metadataRepository,
                            regions = regions,
                            location = location
                        )
                    Triple(cards, insideExplore, location)
                }
                .collect { (cards, insideExplore, location) ->
                    _uiState.update { state ->
                        val waitingForFix =
                            location == null && state.locationAccessState == LocationAccessState.GRANTED
                        state.copy(
                            regions = cards,
                            loading = false,
                            insideRegionIds = insideExplore.map { it.id }.toSet(),
                            enterExploreEnabled = HomeRegionLogic.isEnterExploreEnabled(insideExplore),
                            waitingForGpsFix = waitingForFix
                        )
                    }
                }
        }
        viewModelScope.launch {
            locationRepository.locationFlow
                .sample(INSIDE_REGION_THROTTLE_MS)
                .collect { location ->
                    currentLocation.value = location
                    _uiState.update { state ->
                        state.copy(
                            waitingForGpsFix = false,
                            locationAccessState =
                            if (state.locationAccessState == LocationAccessState.WAITING_FOR_FIX) {
                                LocationAccessState.GRANTED
                            } else {
                                state.locationAccessState
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
            seedCachedLocation()
        } else {
            currentLocation.value = null
            insideExploreRegions.value = emptyList()
            locationRepository.onPermissionRevoked()
            declinationCorrector.clearSeedLocation()
        }
        _uiState.update { state ->
            val waiting =
                accessState == LocationAccessState.GRANTED &&
                    currentLocation.value == null
            state.copy(
                locationAccessState = if (waiting) LocationAccessState.WAITING_FOR_FIX else accessState,
                waitingForGpsFix = waiting,
                insideRegionIds = emptySet(),
                enterExploreEnabled = false
            )
        }
    }

    private fun seedCachedLocation() {
        locationRepository.lastLocation?.let { currentLocation.value = it }
    }

    private fun refreshStorageStats() {
        viewModelScope.launch(Dispatchers.IO) {
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

    fun onGlobalEnterExploreClicked() {
        exploreNavigation.onGlobalEnterExplore(insideExploreRegions.value)
    }

    fun onExploreUiAction(action: ExploreUiAction) {
        when (action) {
            ExploreUiAction.NavigationHandled -> exploreNavigation.onExploreNavigationHandled()
        }
    }

    fun onDeleteClicked(regionId: UUID) {
        viewModelScope.launch {
            regionRepository.getRegion(regionId)?.let { region ->
                _uiState.update { it.copy(deleteConfirmRegion = region) }
            }
        }
    }

    fun confirmDeleteRegion() {
        val region = _uiState.value.deleteConfirmRegion ?: return
        viewModelScope.launch {
            regionDeletionUseCase.execute(region.id)
            _uiState.update {
                it.copy(deleteConfirmRegion = null, snackbarMessage = "${region.name} deleted")
            }
            refreshStorageStats()
        }
    }

    fun dismissDeleteRegion() {
        _uiState.update { it.copy(deleteConfirmRegion = null) }
    }

    fun onSnackbarShown() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    override fun onCleared() {
        locationController.release(HOME_LOCATION_TOKEN)
        super.onCleared()
    }

    companion object {
        private const val HOME_LOCATION_TOKEN = "home"
        private const val INSIDE_REGION_THROTTLE_MS = 1_000L
    }
}

internal fun readFreeSpaceBytes(context: Context): Long = runCatching {
    val stat = StatFs(context.filesDir.path)
    stat.availableBlocksLong * stat.blockSizeLong
}.getOrDefault(0L)
