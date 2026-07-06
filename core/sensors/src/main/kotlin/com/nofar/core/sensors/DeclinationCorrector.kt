package com.nofar.core.sensors

import com.nofar.core.location.LocationRepository
import com.nofar.core.model.AppConfig
import com.nofar.core.model.RegionBounds
import com.nofar.core.model.UserLocation
import javax.inject.Inject

/**
 * Applies magnetic declination correction using GPS-seeded [GeomagneticField] (Requirements §3.3).
 */
class DeclinationCorrector
@Inject
constructor(
    private val declinationProvider: DeclinationProvider,
    private val locationRepository: LocationRepository
) {
    private var declinationDeg: Float = 0f
    private var seedLocation: UserLocation? = null

    fun magneticToTrueAzimuth(magneticAzimuthDeg: Float): Float {
        updateDeclinationIfNeeded()
        return normalizeAzimuth(magneticAzimuthDeg + declinationDeg)
    }

    fun currentDeclinationDegrees(): Float {
        updateDeclinationIfNeeded()
        return declinationDeg
    }

    fun clearSeedLocation() {
        seedLocation = null
        declinationDeg = 0f
    }

    private fun updateDeclinationIfNeeded() {
        val location = locationRepository.lastLocation ?: return
        val seed = seedLocation
        if (seed == null || movedSignificantly(seed, location)) {
            seedLocation = location
            declinationDeg =
                declinationProvider.getDeclinationDegrees(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitudeMeters = location.altitudeMeters ?: 0.0,
                    timeMillis = location.timestampMillis
                )
        }
    }

    private fun movedSignificantly(from: UserLocation, to: UserLocation): Boolean = RegionBounds.haversineDistanceM(
        from.latitude,
        from.longitude,
        to.latitude,
        to.longitude
    ) >= AppConfig.DECLINATION_UPDATE_DISTANCE_METERS

    private fun normalizeAzimuth(degrees: Float): Float {
        var normalized = degrees % 360f
        if (normalized < 0f) normalized += 360f
        return normalized
    }
}
