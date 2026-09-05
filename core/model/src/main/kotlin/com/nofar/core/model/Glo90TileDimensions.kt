package com.nofar.core.model

import kotlin.math.abs

/**
 * Copernicus GLO-90 1° cell raster dimensions (width varies by latitude; height is always 1200).
 *
 * Used for download size estimates and byte-budget gates.
 */
object Glo90TileDimensions {
    const val HEIGHT = 1_200

    /** On-disk `.bin` payload bytes at 1200×1200 int16 (excludes 49-byte header). */
    const val BASE_DISK_PAYLOAD_BYTES: Long = 1_200L * 1_200 * 2

    /** Typical compressed GeoTIFF wire size at 1200×1200 (bytes). */
    const val BASE_WIRE_BYTES: Long = 5_400_000L

    /**
     * Width for a 1° cell whose lower-left latitude is [tileLat].
     * Band boundaries use the cell midpoint so S50 (covers 50°S–49°S) stays 1200-wide.
     */
    fun widthForLatitude(lat: Double): Int = widthForAbsLatitude(abs(lat))

    fun widthForTileLat(tileLat: Int): Int = widthForAbsLatitude(abs(tileLat + 0.5))

    fun widthForAbsLatitude(absLat: Double): Int = when {
        absLat < 50.0 -> 1_200
        absLat < 60.0 -> 800
        absLat < 70.0 -> 600
        absLat < 80.0 -> 400
        absLat < 85.0 -> 240
        else -> 120
    }

    fun diskBytes(tileLat: Int, headerSizeBytes: Int = 49): Long {
        val width = widthForTileLat(tileLat)
        return headerSizeBytes.toLong() + width.toLong() * HEIGHT * 2
    }

    fun wireBytes(tileLat: Int): Long {
        val width = widthForTileLat(tileLat)
        return (BASE_WIRE_BYTES * width / 1_200.0).toLong()
    }

    fun totalDiskBytes(cells: Iterable<Pair<Int, Int>>, headerSizeBytes: Int = 49): Long =
        cells.sumOf { (lat, _) -> diskBytes(lat, headerSizeBytes) }

    fun totalWireBytes(cells: Iterable<Pair<Int, Int>>): Long = cells.sumOf { (lat, _) -> wireBytes(lat) }
}
