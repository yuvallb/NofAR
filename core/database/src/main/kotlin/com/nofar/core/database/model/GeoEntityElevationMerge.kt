package com.nofar.core.database.model

/**
 * OSM ingest often omits `ele`; DEM post-processing then fills elevation.
 * On re-download / overlapping regions, an OSM upsert with null elevation must not
 * wipe a previously sampled DEM (or OSM) value.
 */
object GeoEntityElevationMerge {
    fun resolve(
        incomingElevation: Int?,
        incomingSource: String?,
        existingElevation: Int?,
        existingSource: String?
    ): Pair<Int?, String?> = if (incomingElevation != null) {
        incomingElevation to incomingSource
    } else {
        existingElevation to existingSource
    }
}
