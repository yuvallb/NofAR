package com.nofar.core.location

import com.nofar.core.model.UserLocation
import kotlinx.coroutines.flow.Flow

/**
 * GPS-only location stream (Requirements §5.1 privacy).
 */
interface LocationProvider {
    val locationFlow: Flow<UserLocation>
    val lastLocation: UserLocation?

    fun startUpdates()

    fun stopUpdates()

    fun clearLastLocation()
}
