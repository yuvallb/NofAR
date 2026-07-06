package com.nofar.core.visibility

import com.nofar.core.data.dem.DemTileReader
import com.nofar.core.model.DemTileId

/**
 * Multi-tile elevation lookup for ray-marching. Selects the correct [DemTileReader] by lat/lon.
 *
 * **No-data handling:** samples with no elevation (missing tile or no-data pixel) are skipped
 * during occlusion checks — they do not block line of sight, matching the Python prototype.
 */
class DemElevationSampler(demReaders: Map<String, DemTileReader>) {
    private val readersByOrigin: Map<Pair<Int, Int>, DemTileReader> =
        demReaders.mapNotNull { (tileId, reader) ->
            DemTileId.parse(tileId)?.let { origin -> origin to reader }
        }.toMap()

    fun elevationAt(lat: Double, lon: Double): Float? {
        val (tileLat, tileLon) = DemTileId.coordinatesForPoint(lat, lon)
        return readersByOrigin[tileLat to tileLon]?.elevationAt(lat, lon)
    }

    fun hasTileForPoint(lat: Double, lon: Double): Boolean {
        val (tileLat, tileLon) = DemTileId.coordinatesForPoint(lat, lon)
        return readersByOrigin.containsKey(tileLat to tileLon)
    }
}
