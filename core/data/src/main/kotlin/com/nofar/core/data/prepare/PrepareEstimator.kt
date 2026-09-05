package com.nofar.core.data.prepare

import com.nofar.core.model.DemTileId
import com.nofar.core.model.Glo90TileDimensions
import kotlin.math.cos
import kotlin.math.max

data class DownloadEstimate(
    val osmEstimateBytes: Long,
    val demTileCount: Int,
    val demEstimateMinBytes: Long,
    val demEstimateMaxBytes: Long
) {
    val totalMinBytes: Long get() = osmEstimateBytes + demEstimateMinBytes
    val totalMaxBytes: Long get() = osmEstimateBytes + demEstimateMaxBytes

    /** Prefer on-disk DEM + OSM for UI / cache gates; wire size is demEstimateMaxBytes. */
    val totalEstimateBytes: Long get() = osmEstimateBytes + demEstimateMinBytes
    val totalWireBytes: Long get() = osmEstimateBytes + demEstimateMaxBytes
}

object PrepareEstimator {
    /** Sparse place/peak density for city|town|village|peak only (bytes per km²). */
    private const val OSM_BYTES_PER_SQ_KM = 80L
    private const val EQUATORIAL_KM_PER_DEG = 111.32
    private const val MIN_OSM_BYTES = 64_000L

    fun estimateForCells(cells: List<Pair<Int, Int>>): DownloadEstimate {
        val demDisk = Glo90TileDimensions.totalDiskBytes(cells)
        val demWire = Glo90TileDimensions.totalWireBytes(cells)
        val osmEstimate =
            max(
                MIN_OSM_BYTES,
                cells.sumOf { (tileLat, _) ->
                    val midLat = tileLat + 0.5
                    val heightKm = EQUATORIAL_KM_PER_DEG
                    val widthKm = EQUATORIAL_KM_PER_DEG * cos(Math.toRadians(midLat)).coerceAtLeast(0.01)
                    (heightKm * widthKm * OSM_BYTES_PER_SQ_KM).toLong()
                }
            )
        return DownloadEstimate(
            osmEstimateBytes = osmEstimate,
            demTileCount = cells.size,
            demEstimateMinBytes = demDisk,
            demEstimateMaxBytes = demWire
        )
    }

    /** @deprecated Use [estimateForCells]. */
    fun estimate(centerLat: Double, centerLon: Double, radiusM: Double): DownloadEstimate {
        val bbox = com.nofar.core.model.GeoMathBounds.boundingBox(centerLat, centerLon, radiusM)
        return estimateForCells(DemTileId.intersectingTiles(bbox))
    }
}
