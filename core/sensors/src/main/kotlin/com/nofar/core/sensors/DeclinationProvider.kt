package com.nofar.core.sensors

/**
 * Abstraction over magnetic declination for testability (AC-3.3).
 */
fun interface DeclinationProvider {
    fun getDeclinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double, timeMillis: Long): Float
}
