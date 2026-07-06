package com.nofar.core.location

import com.nofar.core.model.AppConfig
import com.nofar.core.model.RegionBounds
import com.nofar.core.model.UserLocation

/**
 * Emits when the user has moved farther than [AppConfig.VISIBILITY_REFRESH_DISTANCE_METERS]
 * since the last visibility pass anchor.
 */
class LocationMovedTracker {
    private var anchor: UserLocation? = null

    fun onLocationUpdate(location: UserLocation): UserLocation? {
        val previous = anchor
        if (previous == null) {
            anchor = location
            return null
        }
        val distanceM =
            RegionBounds.haversineDistanceM(
                previous.latitude,
                previous.longitude,
                location.latitude,
                location.longitude
            )
        return if (distanceM >= AppConfig.VISIBILITY_REFRESH_DISTANCE_METERS) {
            anchor = location
            location
        } else {
            null
        }
    }

    fun reset() {
        anchor = null
    }
}
