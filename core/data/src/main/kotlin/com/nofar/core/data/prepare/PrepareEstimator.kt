package com.nofar.core.data.prepare

import com.nofar.core.model.BoundingBox
import com.nofar.core.model.DemTileId
import com.nofar.core.model.RegionBounds
import kotlin.math.max

data class DownloadEstimate(
    val osmEstimateBytes: Long,
    val demTileCount: Int,
    val demEstimateMinBytes: Long,
    val demEstimateMaxBytes: Long
) {
    val totalMinBytes: Long get() = osmEstimateBytes + demEstimateMinBytes
    val totalMaxBytes: Long get() = osmEstimateBytes + demEstimateMaxBytes
    val totalEstimateBytes: Long get() = (totalMinBytes + totalMaxBytes) / 2
}

object PrepareEstimator {
    private const val OSM_BYTES_PER_SQ_KM = 120_000L
    private const val DEM_TILE_MIN_BYTES = 13L * 1024 * 1024
    private const val DEM_TILE_MAX_BYTES = 50L * 1024 * 1024

    fun estimate(centerLat: Double, centerLon: Double, radiusM: Double): DownloadEstimate {
        val bbox = RegionBounds.boundingBox(centerLat, centerLon, radiusM)
        val areaSqKm = estimateAreaSqKm(bbox)
        val demTileCount = DemTileId.intersectingTiles(bbox).size
        return DownloadEstimate(
            osmEstimateBytes = max(512_000L, (areaSqKm * OSM_BYTES_PER_SQ_KM).toLong()),
            demTileCount = demTileCount,
            demEstimateMinBytes = demTileCount * DEM_TILE_MIN_BYTES,
            demEstimateMaxBytes = demTileCount * DEM_TILE_MAX_BYTES
        )
    }

    private fun estimateAreaSqKm(bbox: BoundingBox): Double {
        val latSpanKm = (bbox.maxLat - bbox.minLat) * 111.32
        val lonSpanKm = (bbox.maxLon - bbox.minLon) * 111.32 *
            kotlin.math.cos(Math.toRadians((bbox.minLat + bbox.maxLat) / 2.0))
        return latSpanKm * lonSpanKm
    }
}
