package com.nofar.core.model

enum class AltitudeSource {
    GPS,
    LAST_KNOWN_GPS,
    DEM
}

/**
 * Resolved altitude for the Explore HUD.
 *
 * [isEstimate] drives a tilde prefix on the altitude value. [accuracyMeters] is always shown when
 * the reading is backed by a live GPS fix or DEM sample at the current position.
 */
data class AltitudeReading(
    val meters: Int,
    val source: AltitudeSource,
    val isEstimate: Boolean,
    val accuracyMeters: Int? = null,
    val accuracyIsVertical: Boolean = false
)
