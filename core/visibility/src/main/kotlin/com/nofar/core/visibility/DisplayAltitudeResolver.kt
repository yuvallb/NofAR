package com.nofar.core.visibility

import com.nofar.core.model.AltitudeReading
import com.nofar.core.model.AltitudeSource
import com.nofar.core.model.AppConfig
import com.nofar.core.model.UserLocation
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

@Singleton
class DisplayAltitudeResolver
@Inject
constructor(private val pointDemElevationLookup: DemPointElevationSource) {
    suspend fun resolve(
        location: UserLocation,
        lastKnownGpsAltitudeM: Double?,
        cellIds: Set<String>,
        isVirtual: Boolean = false
    ): AltitudeReading? {
        val demMeters = sampleDemMeters(location, cellIds)
        val liveGps = location.altitudeMeters
        return when {
            isVirtual -> demMeters?.let(::demOnlyReading)
            liveGps != null -> gpsBackedReading(location, liveGps, AltitudeSource.GPS, demMeters)
            lastKnownGpsAltitudeM != null ->
                gpsBackedReading(location, lastKnownGpsAltitudeM, AltitudeSource.LAST_KNOWN_GPS, demMeters)
            else -> demMeters?.let(::demOnlyReading)
        }
    }

    private suspend fun sampleDemMeters(location: UserLocation, cellIds: Set<String>): Int? =
        pointDemElevationLookup.elevationAt(location.latitude, location.longitude, cellIds)?.roundToInt()

    private fun demOnlyReading(demMeters: Int): AltitudeReading = AltitudeReading(
        meters = demMeters,
        source = AltitudeSource.DEM,
        isEstimate = false,
        accuracyMeters = null,
        accuracyIsVertical = false
    )

    private fun gpsBackedReading(
        location: UserLocation,
        gpsMeters: Double,
        source: AltitudeSource,
        demMeters: Int?
    ): AltitudeReading {
        val roundedGps = gpsMeters.roundToInt()
        val demDisagrees =
            demMeters != null &&
                abs(roundedGps - demMeters) > AppConfig.ALTITUDE_GPS_DEM_DISAGREE_METERS
        return AltitudeReading(
            meters = roundedGps,
            source = source,
            isEstimate = false,
            accuracyMeters = location.accuracyMeters?.toInt(),
            accuracyIsVertical = location.verticalAccuracyMeters != null,
            demMeters = if (demDisagrees) demMeters else null
        )
    }
}
