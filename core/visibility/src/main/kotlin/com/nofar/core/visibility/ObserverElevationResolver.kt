package com.nofar.core.visibility

import com.nofar.core.model.AppConfig
import com.nofar.core.model.UserLocation

data class ObserverElevation(val elevationM: Double, val warning: VisibilityWarning?)

/**
 * Resolves observer ground elevation for the visibility pass (Phase 4.6).
 *
 * Priority: GPS altitude when vertical accuracy is present and at or below
 * [AppConfig.GPS_ALTITUDE_ACCURACY_THRESHOLD_METERS] → DEM sample → sea level with warning.
 */
class ObserverElevationResolver {
    fun resolve(location: UserLocation, demElevationM: Float?): ObserverElevation {
        val gpsAltitude = location.altitudeMeters
        val verticalAccuracy = location.verticalAccuracyMeters
        if (gpsAltitude != null &&
            verticalAccuracy != null &&
            verticalAccuracy <= AppConfig.GPS_ALTITUDE_ACCURACY_THRESHOLD_METERS
        ) {
            return ObserverElevation(elevationM = gpsAltitude, warning = null)
        }
        return demElevationM?.let { dem ->
            ObserverElevation(
                elevationM = dem.toDouble(),
                warning = VisibilityWarning.OBSERVER_ELEVATION_FROM_DEM
            )
        } ?: ObserverElevation(
            elevationM = SEA_LEVEL_METERS,
            warning = VisibilityWarning.OBSERVER_ELEVATION_FALLBACK_SEA_LEVEL
        )
    }

    companion object {
        private const val SEA_LEVEL_METERS = 0.0
    }
}
