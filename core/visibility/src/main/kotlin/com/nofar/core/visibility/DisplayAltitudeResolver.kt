package com.nofar.core.visibility

import com.nofar.core.model.AltitudeReading
import com.nofar.core.model.AltitudeSource
import com.nofar.core.model.AppConfig
import com.nofar.core.model.Region
import com.nofar.core.model.UserLocation
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Resolves altitude for the Explore HUD.
 *
 * Priority: GPS altitude → sticky last-known GPS → DEM ground sample. No sea-level fallback.
 */
@Singleton
class DisplayAltitudeResolver
@Inject
constructor(private val pointDemElevationLookup: DemPointElevationSource) {
    suspend fun resolve(location: UserLocation, lastKnownGpsAltitudeM: Double?, region: Region?): AltitudeReading? =
        when {
            location.altitudeMeters != null -> gpsReading(location)
            lastKnownGpsAltitudeM != null -> cachedGpsReading(location, lastKnownGpsAltitudeM)
            else -> demReading(location, region)
        }

    private fun gpsReading(location: UserLocation): AltitudeReading = AltitudeReading(
        meters = location.altitudeMeters!!.roundToInt(),
        source = AltitudeSource.GPS,
        isEstimate = isGpsAltitudeEstimate(location),
        accuracyMeters = gpsAccuracyMeters(location),
        accuracyIsVertical = location.verticalAccuracyMeters != null
    )

    private fun cachedGpsReading(location: UserLocation, cachedAltitudeM: Double): AltitudeReading = AltitudeReading(
        meters = cachedAltitudeM.roundToInt(),
        source = AltitudeSource.LAST_KNOWN_GPS,
        isEstimate = true,
        accuracyMeters = horizontalAccuracyMeters(location),
        accuracyIsVertical = false
    )

    private suspend fun demReading(location: UserLocation, region: Region?): AltitudeReading? {
        val demElevationM =
            pointDemElevationLookup.elevationAt(location.latitude, location.longitude, region)
        return demElevationM?.let { demElevation ->
            AltitudeReading(
                meters = demElevation.roundToInt(),
                source = AltitudeSource.DEM,
                isEstimate = true,
                accuracyMeters = horizontalAccuracyMeters(location),
                accuracyIsVertical = false
            )
        }
    }

    private fun isGpsAltitudeEstimate(location: UserLocation): Boolean {
        val verticalAccuracy = location.verticalAccuracyMeters ?: return true
        return verticalAccuracy > AppConfig.GPS_ALTITUDE_ACCURACY_THRESHOLD_METERS
    }

    private fun gpsAccuracyMeters(location: UserLocation): Int? =
        location.verticalAccuracyMeters?.roundToInt() ?: horizontalAccuracyMeters(location)

    private fun horizontalAccuracyMeters(location: UserLocation): Int? =
        location.accuracyMeters.takeIf { it > 0f }?.roundToInt()
}
