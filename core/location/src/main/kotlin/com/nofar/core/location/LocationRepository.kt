package com.nofar.core.location

import com.nofar.core.model.UserLocation
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.onEach

interface LocationRepository {
    val locationFlow: Flow<UserLocation>
    val significantMoveFlow: SharedFlow<UserLocation>
    val lastLocation: UserLocation?
    val isActive: Boolean

    fun start()

    fun stop()

    fun clearCachedLocation()

    fun onPermissionRevoked()
}

@Singleton
class DefaultLocationRepository
@Inject
constructor(private val locationProvider: LocationProvider) :
    LocationRepository {
    private val movedTracker = LocationMovedTracker()
    private val _significantMoveFlow = MutableSharedFlow<UserLocation>(extraBufferCapacity = 1)
    private var active = false

    override val locationFlow: Flow<UserLocation> =
        locationProvider.locationFlow.onEach { location ->
            movedTracker.onLocationUpdate(location)?.let { moved ->
                _significantMoveFlow.tryEmit(moved)
            }
        }

    override val significantMoveFlow: SharedFlow<UserLocation> = _significantMoveFlow.asSharedFlow()

    override val lastLocation: UserLocation?
        get() = locationProvider.lastLocation

    override val isActive: Boolean
        get() = active

    override fun start() {
        active = true
        locationProvider.startUpdates()
    }

    override fun stop() {
        if (!active) return
        active = false
        locationProvider.stopUpdates()
        movedTracker.reset()
    }

    override fun clearCachedLocation() {
        locationProvider.clearLastLocation()
        movedTracker.reset()
    }

    override fun onPermissionRevoked() {
        locationProvider.stopUpdates()
        clearCachedLocation()
    }
}
