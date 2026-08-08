package com.nofar.core.visibility

import com.nofar.core.data.dem.DemTileReader
import com.nofar.core.model.DemTileId

/**
 * Bilinear multi-tile elevation lookup for virtual-location map viewshed preview only.
 */
class PreviewDemElevationSampler(demReaders: Map<String, DemTileReader>) : DemSampler {
    private val readersByOrigin: Map<Pair<Int, Int>, DemTileReader> =
        demReaders.values.associateBy { reader -> reader.tileLat to reader.tileLon }

    override fun elevationAt(lat: Double, lon: Double): Float? {
        val (tileLat, tileLon) = DemTileId.coordinatesForPoint(lat, lon)
        return readersByOrigin[tileLat to tileLon]?.elevationAtBilinear(lat, lon)
    }
}
