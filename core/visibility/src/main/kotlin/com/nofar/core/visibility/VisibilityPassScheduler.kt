package com.nofar.core.visibility

import android.util.Log
import com.nofar.core.common.DispatcherProvider
import com.nofar.core.location.LocationRepository
import com.nofar.core.model.Region
import com.nofar.core.model.UserLocation
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class VisibilityPassScheduler
@Inject
constructor(
    private val locationRepository: LocationRepository,
    private val visibilityUseCase: RegionVisibilityComputer,
    private val dispatchers: DispatcherProvider
) {
    private val mutex = Mutex()
    private val _visibleEntities = MutableStateFlow<List<VisibleEntity>>(emptyList())
    val visibleEntities: StateFlow<List<VisibleEntity>> = _visibleEntities.asStateFlow()

    private val _warnings = MutableStateFlow<Set<VisibilityWarning>>(emptySet())
    val warnings: StateFlow<Set<VisibilityWarning>> = _warnings.asStateFlow()

    private var activeRegion: Region? = null
    private var lastPassAtMillis: Long = VisibilityPassPolicy.NO_PASS_YET
    private var lastPassLocation: UserLocation? = null
    private var sequenceNumber: Long = 0L
    private var inFlightJob: Job? = null
    private var collectorJob: Job? = null
    private var scope: CoroutineScope? = null

    fun setActiveRegion(region: Region?) {
        activeRegion = region
        lastPassAtMillis = VisibilityPassPolicy.NO_PASS_YET
        lastPassLocation = null
        locationRepository.lastLocation?.let { location ->
            triggerPass(force = true, location = location, region = region)
        }
    }

    fun start(scope: CoroutineScope) {
        if (collectorJob != null) return
        this.scope = scope
        collectorJob =
            scope.launch(dispatchers.default) {
                locationRepository.locationFlow.collect { location ->
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
        _warnings.value = emptySet()
        activeRegion = null
        lastPassAtMillis = VisibilityPassPolicy.NO_PASS_YET
        lastPassLocation = null
    }

    private fun onLocationUpdate(location: UserLocation) {
        if (activeRegion == null) return
        if (shouldSchedulePass(location, force = false)) {
            triggerPass(force = false, location = location, region = activeRegion)
        }
    }

    private fun triggerPass(
        force: Boolean,
        location: UserLocation? = locationRepository.lastLocation,
        region: Region? = activeRegion
    ) {
        val currentRegion = region
        val currentLocation = location
        val launchScope = scope
        if (currentRegion == null || currentLocation == null || launchScope == null) {
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
                        mutex.withLock {
                            visibilityUseCase.computeForRegion(
                                region = currentRegion,
                                location = currentLocation
                            )
                        }
                    }.getOrElse { error ->
                        Log.e(TAG, "Visibility pass failed for region ${currentRegion.id}", error)
                        VisibilityResult(
                            entities = emptyList(),
                            computationTimeMs = 0L,
                            warnings = setOf(VisibilityWarning.DEM_TILE_MISSING)
                        )
                    }
                if (passSequence == sequenceNumber) {
                    lastPassAtMillis = currentLocation.timestampMillis
                    lastPassLocation = currentLocation
                    _visibleEntities.value = result.entities
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
