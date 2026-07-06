package com.nofar.core.model

import kotlin.math.floor

/**
 * Copernicus GLO-30 tile naming: `Copernicus_DSM_COG_10_{N|S}{lat}_00_{E|W}{lon}_00_DEM`.
 */
object DemTileId {
    private val tileIdPattern =
        Regex("""Copernicus_DSM_COG_10_([NS])(\d{2})_00_([EW])(\d{3})_00_DEM""")

    fun fromCoordinates(lat: Int, lon: Int): String {
        val ns = if (lat >= 0) "N" else "S"
        val ew = if (lon >= 0) "E" else "W"
        return "Copernicus_DSM_COG_10_${ns}${kotlin.math.abs(lat).toString().padStart(
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

    fun binFileName(tileId: String): String = "$tileId.bin"

    fun s3ObjectKey(tileId: String): String = "$tileId/$tileId.tif"
}
