package com.nofar.core.visibility

import com.nofar.core.model.AltitudeReading
import com.nofar.core.model.AltitudeSource
import com.nofar.core.model.AppConfig
import com.nofar.core.model.Region
import com.nofar.core.model.UserLocation
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Resolves altitude for the Explore HUD.
 *
 * Virtual Explore: DEM ground only. Live Explore: GPS (or sticky last-known GPS) as the primary
 * value, with DEM attached when the rounded values differ by more than
 * [AppConfig.ALTITUDE_GPS_DEM_DISAGREE_METERS]. Falls back to DEM when no GPS altitude is
 * available. No sea-level fallback.
 */
@Singleton
class DisplayAltitudeResolver
@Inject
constructor(private val pointDemElevationLookup: DemPointElevationSource) {
    suspend fun resolve(
        location: UserLocation,
        lastKnownGpsAltitudeM: Double?,
        region: Region?,
        isVirtual: Boolean = false
    ): AltitudeReading? = resolve(location, lastKnownGpsAltitudeM, listOfNotNull(region), isVirtual)

    suspend fun resolve(
        location: UserLocation,
        lastKnownGpsAltitudeM: Double?,
        regions: List<Region>,
        isVirtual: Boolean = false
    ): AltitudeReading? {
        val demMeters = sampleDemMeters(location, regions)
        val liveGps = location.altitudeMeters
        return when {
            isVirtual -> demMeters?.let(::demOnlyReading)
            liveGps != null -> gpsBackedReading(location, liveGps, AltitudeSource.GPS, demMeters)
            lastKnownGpsAltitudeM != null ->
                gpsBackedReading(location, lastKnownGpsAltitudeM, AltitudeSource.LAST_KNOWN_GPS, demMeters)
            else -> demMeters?.let(::demOnlyReading)
        }
    }

    private suspend fun sampleDemMeters(location: UserLocation, regions: List<Region>): Int? =
        regions.firstNotNullOfOrNull { region ->
            pointDemElevationLookup.elevationAt(location.latitude, location.longitude, region)?.roundToInt()
        }

    private fun demOnlyReading(demMeters: Int): AltitudeReading = AltitudeReading(
        meters = demMeters,
        source = AltitudeSource.DEM,
        isEstimate = false,
        accuracyMeters = null,
        accuracyIsVertical = false
    )

    private fun gpsBackedReading(
        location: UserLocation,
        gpsAltitudeM: Double,
        source: AltitudeSource,
        demMeters: Int?
    ): AltitudeReading {
        val gpsMeters = gpsAltitudeM.roundToInt()
        val delta = demMeters?.let { gpsMeters - it }
        val showDem = delta != null && abs(delta) > AppConfig.ALTITUDE_GPS_DEM_DISAGREE_METERS
        val liveGps = source == AltitudeSource.GPS
        return AltitudeReading(
            meters = gpsMeters,
            source = source,
            isEstimate = if (liveGps) isGpsAltitudeEstimate(location) else true,
            accuracyMeters = if (liveGps) gpsAccuracyMeters(location) else horizontalAccuracyMeters(location),
            accuracyIsVertical = liveGps && location.verticalAccuracyMeters != null,
            demMeters = if (showDem) demMeters else null,
            demDeltaMeters = if (showDem) delta else null
        )
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
