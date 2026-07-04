package com.nofar.core.model

/**
 * Copernicus GLO-30 tile naming: `Copernicus_DSM_COG_10_N{lat}_00_E{lon}_00_DEM`.
 */
object DemTileId {
    private val tileIdPattern =
        Regex("""Copernicus_DSM_COG_10_N(\d{2})_00_E(\d{3})_00_DEM""")

    fun fromCoordinates(tileLat: Int, tileLon: Int): String {
        require(tileLat in 0..89) { "tileLat must be 0..89, got $tileLat" }
        require(tileLon in 0..179) { "tileLon must be 0..179, got $tileLon" }
        return "Copernicus_DSM_COG_10_N${tileLat.toString().padStart(
            2,
            '0'
        )}_00_E${tileLon.toString().padStart(3, '0')}_00_DEM"
    }

    fun parse(tileId: String): Pair<Int, Int>? {
        val match = tileIdPattern.matchEntire(tileId) ?: return null
        return match.groupValues[1].toInt() to match.groupValues[2].toInt()
    }

    fun coordinatesForPoint(lat: Double, lon: Double): Pair<Int, Int> {
        val tileLat = kotlin.math.floor(lat).toInt()
        val tileLon = kotlin.math.floor(lon).toInt()
        return tileLat to tileLon
    }

    fun binFileName(tileId: String): String = "$tileId.bin"
}
