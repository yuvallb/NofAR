package com.nofar.core.model

import kotlin.math.floor

/**
 * Copernicus GLO-90 tile naming: `Copernicus_DSM_COG_30_{N|S}{lat}_00_{E|W}{lon}_00_DEM`.
 */
object DemTileId {
    private val tileIdPattern =
        Regex("""Copernicus_DSM_COG_30_([NS])(\d{2})_00_([EW])(\d{3})_00_DEM""")

    fun fromCoordinates(lat: Int, lon: Int): String {
        val ns = if (lat >= 0) "N" else "S"
        val ew = if (lon >= 0) "E" else "W"
        return "Copernicus_DSM_COG_30_${ns}${kotlin.math.abs(lat).toString().padStart(
            2,
            '0'
        )}_00_${ew}${kotlin.math.abs(lon).toString().padStart(3, '0')}_00_DEM"
    }

    fun parse(tileId: String): Pair<Int, Int>? {
        val match = tileIdPattern.matchEntire(tileId) ?: return null
        val latSign = if (match.groupValues[1] == "N") 1 else -1
        val lonSign = if (match.groupValues[3] == "E") 1 else -1
        return match.groupValues[2].toInt() * latSign to match.groupValues[4].toInt() * lonSign
    }

    fun coordinatesForPoint(lat: Double, lon: Double): Pair<Int, Int> {
        val tileLat = floor(lat).toInt()
        val tileLon = floor(lon).toInt()
        return tileLat to tileLon
    }

    fun cellForPoint(lat: Double, lon: Double): Pair<Int, Int> = coordinatesForPoint(lat, lon)

    fun intersectingTiles(bbox: BoundingBox): List<Pair<Int, Int>> {
        val minLat = floor(bbox.minLat).toInt()
        val maxLat = floor(bbox.maxLat).toInt()
        val minLon = floor(bbox.minLon).toInt()
        val maxLon = floor(bbox.maxLon).toInt()
        val tiles = mutableListOf<Pair<Int, Int>>()
        for (lat in minLat..maxLat) {
            for (lon in minLon..maxLon) {
                tiles += lat to lon
            }
        }
        return tiles
    }

    /** 3×3 ring of 1° cells around the observer cell (includes center). Wraps longitude; clamps latitude. */
    fun localCellRing(lat: Double, lon: Double): List<Pair<Int, Int>> {
        val (centerLat, centerLon) = cellForPoint(lat, lon)
        return buildList {
            for (dLat in -1..1) {
                val tileLat = (centerLat + dLat).coerceIn(-90, 89)
                for (dLon in -1..1) {
                    add(tileLat to wrapLon(centerLon + dLon))
                }
            }
        }.distinct()
    }

    /** Normalize longitude cell index into [-180, 179]. */
    fun wrapLon(lon: Int): Int {
        var wrapped = lon % 360
        if (wrapped < -180) wrapped += 360
        if (wrapped >= 180) wrapped -= 360
        return wrapped
    }

    fun binFileName(tileId: String): String = "$tileId.bin"

    fun s3ObjectKey(tileId: String): String = "$tileId/$tileId.tif"
}
