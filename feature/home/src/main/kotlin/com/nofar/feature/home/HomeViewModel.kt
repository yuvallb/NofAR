package com.nofar.feature.home

import android.content.Context
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nofar.core.data.preferences.UserPreferencesRepository
import com.nofar.core.data.repository.CoverageSetRepository
import com.nofar.core.data.repository.HomeCoverageSetMetadataRepository
import com.nofar.core.data.repository.StorageRepository
import com.nofar.core.data.usecase.CoverageSetDeletionUseCase
import com.nofar.core.data.usecase.CoverageSetRepairUseCase
import com.nofar.core.data.usecase.InsideCoverageUseCase
import com.nofar.core.designsystem.component.CoverageSetCardState
import com.nofar.core.location.LocationController
import com.nofar.core.location.LocationRepository
import com.nofar.core.model.CoverageSet
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.LocationAccessState
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
    val coverageSets: List<CoverageSetCardState> = emptyList(),
    val insideCoverageSetIds: Set<UUID> = emptySet(),
    val enterExploreEnabled: Boolean = false,
    val demCacheBytes: Long = 0L,
    val entitiesDbBytes: Long = 0L,
    val freeSpaceBytes: Long = 0L,
    val deleteConfirmCoverageSet: CoverageSet? = null,
    val navigateToExploreCoverageSetId: UUID? = null,
    val snackbarMessage: String? = null,
    val locationAccessState: LocationAccessState = LocationAccessState.NOT_REQUESTED,
    val waitingForGpsFix: Boolean = false,
    val loading: Boolean = true,
    val simpleModeEnabled: Boolean = true,
    val exploreAnotherLocationEnabled: Boolean = false
)

@HiltViewModel
class HomeViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val coverageSetRepository: CoverageSetRepository,
    private val storageRepository: StorageRepository,
    private val coverageSetDeletionUseCase: CoverageSetDeletionUseCase,
    private val insideCoverageUseCase: InsideCoverageUseCase,
    private val metadataRepository: HomeCoverageSetMetadataRepository,
    private val coverageSetRepairUseCase: CoverageSetRepairUseCase,
    private val locationRepository: LocationRepository,
    private val locationController: LocationController,
    private val declinationCorrector: DeclinationCorrector,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {
    private val currentLocation = MutableStateFlow<UserLocation?>(null)
    private val insideExploreCoverageSets = MutableStateFlow<List<CoverageSet>>(emptyList())
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private val exploreNavigation = HomeExploreNavigation(_uiState)

    init {
        seedCachedLocation()
        locationController.acquire(HOME_LOCATION_TOKEN)
        viewModelScope.launch(Dispatchers.IO) {
            coverageSetRepository.observeAllCoverageSets().collect { coverageSets ->
                coverageSets.forEach { set ->
                    runCatching { coverageSetRepairUseCase.repairIfNeeded(set) }
                }
            }
        }
        viewModelScope.launch {
            combine(
                coverageSetRepository.observeAllCoverageSets(),
                currentLocation
            ) { coverageSets, location -> coverageSets to location }
                .mapLatest { (coverageSets, location) ->
                    val cellIdsBySet =
                        coverageSets.associate { set ->
                            set.id to coverageSetRepository.getCellIdsForCoverageSet(set.id).toSet()
                        }
                    val insideExplore = HomeCoverageLogic.exploreEligibleInside(coverageSets, location, cellIdsBySet)
                    insideExploreCoverageSets.value = insideExplore
                    val cards =
                        buildHomeCoverageSetCards(
                            insideCoverageUseCase = insideCoverageUseCase,
                            metadataRepository = metadataRepository,
                            coverageSetRepository = coverageSetRepository,
                            coverageSets = coverageSets,
                            location = location
                        )
                    Triple(cards, insideExplore, location)
                }
                .flowOn(Dispatchers.IO)
                .collect { (cards, insideExplore, location) ->
                    _uiState.update { state ->
                        val waitingForFix =
                            location == null && state.locationAccessState == LocationAccessState.GRANTED
                        val exploreAnother =
                            !state.simpleModeEnabled &&
                                cards.any { card ->
                                    card.coverageSet.downloadStatus == DownloadStatus.READY ||
                                        card.coverageSet.downloadStatus == DownloadStatus.PARTIAL
                                }
                        state.copy(
                            coverageSets = cards,
                            loading = false,
                            insideCoverageSetIds = insideExplore.map { it.id }.toSet(),
                            enterExploreEnabled = HomeCoverageLogic.isEnterExploreEnabled(insideExplore),
                            waitingForGpsFix = waitingForFix,
                            exploreAnotherLocationEnabled = exploreAnother
                        )
                    }
                }
        }
        viewModelScope.launch {
            locationRepository.locationFlow
                .sample(INSIDE_COVERAGE_THROTTLE_MS)
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
        viewModelScope.launch {
            userPreferencesRepository.simpleModeEnabled.collect { simpleMode ->
                _uiState.update { state ->
                    state.copy(
                        simpleModeEnabled = simpleMode,
                        exploreAnotherLocationEnabled =
                        !simpleMode &&
                            state.coverageSets.any { card ->
                                card.coverageSet.downloadStatus == DownloadStatus.READY ||
                                    card.coverageSet.downloadStatus == DownloadStatus.PARTIAL
                            }
                    )
                }
            }
        }
    }

    fun onLocationPermissionChanged(accessState: LocationAccessState) {
        if (accessState == LocationAccessState.GRANTED) {
            locationRepository.start()
            seedCachedLocation()
        } else {
            currentLocation.value = null
            insideExploreCoverageSets.value = emptyList()
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
                insideCoverageSetIds = emptySet(),
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
        exploreNavigation.onGlobalEnterExplore(insideExploreCoverageSets.value)
    }

    fun onExploreNavigationHandled() {
        exploreNavigation.onExploreNavigationHandled()
    }

    fun onDeleteClicked(coverageSetId: UUID) {
        viewModelScope.launch {
            coverageSetRepository.getCoverageSet(coverageSetId)?.let { coverageSet ->
                _uiState.update { it.copy(deleteConfirmCoverageSet = coverageSet) }
            }
        }
    }

    fun confirmDeleteCoverageSet() {
        val coverageSet = _uiState.value.deleteConfirmCoverageSet ?: return
        viewModelScope.launch {
            coverageSetDeletionUseCase.execute(coverageSet.id)
            _uiState.update {
                it.copy(deleteConfirmCoverageSet = null, snackbarMessage = "${coverageSet.name} deleted")
            }
            refreshStorageStats()
        }
    }

    fun dismissDeleteCoverageSet() {
        _uiState.update { it.copy(deleteConfirmCoverageSet = null) }
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
        private const val INSIDE_COVERAGE_THROTTLE_MS = 1_000L
    }
}

internal fun readFreeSpaceBytes(context: Context): Long = runCatching {
    val stat = StatFs(context.filesDir.path)
    stat.availableBlocksLong * stat.blockSizeLong
}.getOrDefault(0L)
