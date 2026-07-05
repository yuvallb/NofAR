package com.nofar.core.sensors

import android.hardware.GeomagneticField
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidDeclinationProvider
@Inject
constructor() : DeclinationProvider {
    override fun getDeclinationDegrees(
        latitude: Double,
        longitude: Double,
        altitudeMeters: Double,
        timeMillis: Long
    ): Float {
        val field =
            GeomagneticField(
                latitude.toFloat(),
                longitude.toFloat(),
                altitudeMeters.toFloat(),
                timeMillis
            )
        return field.declination
    }
}
