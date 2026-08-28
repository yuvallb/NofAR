package com.nofar.core.model

enum class AltitudeSource {
    GPS,
    LAST_KNOWN_GPS,
    DEM
}

/**
 * Resolved altitude for the Explore HUD.
 *
 * [meters] is GPS (or last-known GPS) in live Explore, and DEM ground in virtual Explore.
 * [accuracyMeters] is GPS vertical (or horizontal fallback) accuracy; omitted for DEM-only.
 * [demMeters] / [demDeltaMeters] are set only when live GPS and DEM disagree by more than
 * [AppConfig.ALTITUDE_GPS_DEM_DISAGREE_METERS]. [demDeltaMeters] is GPS − DEM.
 */
data class AltitudeReading(
    val meters: Int,
    val source: AltitudeSource,
    val isEstimate: Boolean,
    val accuracyMeters: Int? = null,
    val accuracyIsVertical: Boolean = false,
    val demMeters: Int? = null,
    val demDeltaMeters: Int? = null
) {
    /** Parenthetical DEM comparison, e.g. `(140+19m)`. Null when GPS and DEM agree or DEM is absent. */
    val demDisagreementText: String?
        get() {
            val dem = demMeters ?: return null
            val delta = demDeltaMeters ?: return null
            val signedDelta = if (delta > 0) "+$delta" else "$delta"
            return "($dem${signedDelta}m)"
        }
}
