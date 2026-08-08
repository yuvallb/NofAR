package com.nofar.core.visibility

import android.util.Log
import com.nofar.core.common.DispatcherProvider
import com.nofar.core.data.preferences.UserPreferencesRepository
import com.nofar.core.model.Region
import com.nofar.core.model.UserLocation
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class VisibilityPassScheduler
@Inject
constructor(
    private val visibilityUseCase: RegionVisibilityComputer,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val dispatchers: DispatcherProvider
) {
    private val mutex = Mutex()
    private val _hereContext = MutableStateFlow(HereContext())
    val hereContext: StateFlow<HereContext> = _hereContext.asStateFlow()

    private val _visibleEntities = MutableStateFlow<List<VisibleEntity>>(emptyList())
    val visibleEntities: StateFlow<List<VisibleEntity>> = _visibleEntities.asStateFlow()

    private val _horizonProfile = MutableStateFlow<HorizonProfile?>(null)
    val horizonProfile: StateFlow<HorizonProfile?> = _horizonProfile.asStateFlow()

    private val _horizonEyeSource = MutableStateFlow<ObserverEyeSource?>(null)
    val horizonEyeSource: StateFlow<ObserverEyeSource?> = _horizonEyeSource.asStateFlow()

    private val _warnings = MutableStateFlow<Set<VisibilityWarning>>(emptySet())
    val warnings: StateFlow<Set<VisibilityWarning>> = _warnings.asStateFlow()

    private var activeRegions: List<Region> = emptyList()
    private var lastPassAtMillis: Long = VisibilityPassPolicy.NO_PASS_YET
    private var lastPassLocation: UserLocation? = null
    private var sequenceNumber: Long = 0L
    private var inFlightJob: Job? = null
    private var collectorJob: Job? = null
    private var scope: CoroutineScope? = null
    private var observerLocationFlow: Flow<UserLocation>? = null
    private var lastObserverLocation: UserLocation? = null

    fun configureObserverLocation(observerFlow: Flow<UserLocation>) {
        observerLocationFlow = observerFlow
    }

    fun setActiveRegions(regions: List<Region>) {
        activeRegions = regions
        lastPassAtMillis = VisibilityPassPolicy.NO_PASS_YET
        lastPassLocation = null
        lastObserverLocation?.let { location ->
            triggerPass(force = true, location = location, regions = regions)
        }
    }

    fun seedObserverLocation(location: UserLocation) {
        lastObserverLocation = location
    }

    fun start(scope: CoroutineScope) {
        val flow = observerLocationFlow ?: error("configureObserverLocation before start")
        collectorJob?.cancel()
        inFlightJob?.cancel()
        inFlightJob = null
        this.scope = scope
        collectorJob =
            scope.launch(dispatchers.default) {
                flow.collect { location ->
                    lastObserverLocation = location
                    onLocationUpdate(location)
                }
            }
    }

    fun stop() {
        collectorJob?.cancel()
        collectorJob = null
        inFlightJob?.cancel()
        inFlightJob = null
        scope = null
        _visibleEntities.value = emptyList()
        _hereContext.value = HereContext()
        _horizonProfile.value = null
        _horizonEyeSource.value = null
        _warnings.value = emptySet()
        activeRegions = emptyList()
        lastPassAtMillis = VisibilityPassPolicy.NO_PASS_YET
        lastPassLocation = null
        lastObserverLocation = null
    }

    private fun onLocationUpdate(location: UserLocation) {
        if (activeRegions.isEmpty()) return
        if (shouldSchedulePass(location, force = false)) {
            triggerPass(force = false, location = location, regions = activeRegions)
        }
    }

    private fun triggerPass(
        force: Boolean,
        location: UserLocation? = lastObserverLocation,
        regions: List<Region> = activeRegions
    ) {
        val currentRegions = regions
        val currentLocation = location
        val launchScope = scope
        if (currentRegions.isEmpty() || currentLocation == null || launchScope == null) {
            return
        }
        if (!force && !shouldSchedulePass(currentLocation, force = false)) {
            return
        }

        val passSequence = ++sequenceNumber
        inFlightJob?.cancel()
        inFlightJob =
            launchScope.launch(dispatchers.default) {
                val result =
                    runCatching {
                        val computeHorizonProfile = userPreferencesRepository.showHorizonOutline.first()
                        mutex.withLock {
                            visibilityUseCase.computeForRegions(
                                regions = currentRegions,
                                location = currentLocation,
                                computeHorizonProfile = computeHorizonProfile
                            )
                        }
                    }.getOrElse { error ->
                        Log.e(
                            TAG,
                            "Visibility pass failed for ${currentRegions.size} region(s)",
                            error
                        )
                        VisibilityResult(
                            entities = emptyList(),
                            computationTimeMs = 0L,
                            warnings = emptySet(),
                            hereContext = HereContext()
                        )
                    }
                if (passSequence == sequenceNumber) {
                    lastPassAtMillis = currentLocation.timestampMillis
                    lastPassLocation = currentLocation
                    _visibleEntities.value = result.entities
                    _hereContext.value = result.hereContext
                    _horizonProfile.value = result.horizonProfile
                    _horizonEyeSource.value = result.horizonEyeSource
                    _warnings.value = result.warnings
                }
            }
    }

    private fun shouldSchedulePass(location: UserLocation, force: Boolean): Boolean =
        VisibilityPassPolicy.shouldSchedulePass(
            location = location,
            lastPassLocation = lastPassLocation,
            lastPassAtMillis = lastPassAtMillis,
            force = force
        )

    companion object {
        private const val TAG = "VisibilityPassScheduler"
    }
}
