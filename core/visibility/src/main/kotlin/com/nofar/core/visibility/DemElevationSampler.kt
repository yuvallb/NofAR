package com.nofar.core.visibility

import com.nofar.core.data.dem.DemTileReader
import com.nofar.core.model.DemTileId

/**
 * Minimal elevation-lookup seam. Lets terrain sweeps be unit-tested with in-memory samplers instead
 * of on-disk DEM tiles, while production uses [DemElevationSampler].
 */
fun interface DemSampler {
    fun elevationAt(lat: Double, lon: Double): Float?
}

/**
 * Multi-tile elevation lookup for ray-marching. Selects the correct [DemTileReader] by lat/lon.
 *
 * **No-data handling:** [elevationAt] returns null for missing tiles or no-data pixels.
 * Callers that march terrain (e.g. [TerrainRayMarcher]) treat null as blocking / not visible
 * (fail closed).
 */
class DemElevationSampler(demReaders: Map<String, DemTileReader>) : DemSampler {
    private val readersByOrigin: Map<Pair<Int, Int>, DemTileReader> =
        demReaders.values.associateBy { reader -> reader.tileLat to reader.tileLon }

    override fun elevationAt(lat: Double, lon: Double): Float? {
        val (tileLat, tileLon) = DemTileId.coordinatesForPoint(lat, lon)
        return readersByOrigin[tileLat to tileLon]?.elevationAt(lat, lon)
    }

    fun hasTileForPoint(lat: Double, lon: Double): Boolean {
        val (tileLat, tileLon) = DemTileId.coordinatesForPoint(lat, lon)
        return readersByOrigin.containsKey(tileLat to tileLon)
    }
}
