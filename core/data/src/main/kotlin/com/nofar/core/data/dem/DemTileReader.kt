package com.nofar.core.data.dem

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor

/**
 * Explore-time random-access elevation lookup from a converted `.bin` tile.
 */
class DemTileReader private constructor(
    private val file: RandomAccessFile,
    val width: Int,
    val height: Int,
    val tileLat: Int,
    val tileLon: Int,
    val noDataValue: Float
) : Closeable {
    override fun close() {
        file.close()
    }

    fun elevationAt(lat: Double, lon: Double): Float? {
        if (!isInsideTile(lat, lon)) return null

        val x = pixelX(lon)
        val y = pixelY(lat)
        val offset = DemBinaryFormat.HEADER_SIZE_BYTES + (y * width + x) * Float.SIZE_BYTES
        file.seek(offset.toLong())
        val bytes = ByteArray(Float.SIZE_BYTES)
        file.readFully(bytes)
        val value = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).float
        return if (!value.isFinite() || value == noDataValue) null else value
    }

    private fun isInsideTile(lat: Double, lon: Double): Boolean {
        val lat0 = tileLat.toDouble()
        val lat1 = tileLat + 1.0
        val lon0 = tileLon.toDouble()
        val lon1 = tileLon + 1.0
        return lat >= lat0 && lat < lat1 && lon >= lon0 && lon < lon1
    }

    private fun pixelX(lon: Double): Int {
        val lon0 = tileLon.toDouble()
        val lon1 = tileLon + 1.0
        return floor((lon - lon0) / (lon1 - lon0) * (width - 1)).toInt().coerceIn(0, width - 1)
    }

    private fun pixelY(lat: Double): Int {
        val lat0 = tileLat.toDouble()
        val lat1 = tileLat + 1.0
        return floor((lat1 - lat) / (lat1 - lat0) * (height - 1)).toInt().coerceIn(0, height - 1)
    }

    companion object {
        fun open(file: File): DemTileReader {
            val raf = RandomAccessFile(file, "r")
            val headerBytes = ByteArray(DemBinaryFormat.HEADER_SIZE_BYTES)
            raf.readFully(headerBytes)
            val header = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = CharArray(DemBinaryFormat.MAGIC_SIZE_BYTES) { header.get().toInt().toChar() }.concatToString()
            require(magic == DemBinaryFormat.MAGIC) { "Invalid DEM magic: $magic" }
            val width = header.getInt()
            val height = header.getInt()
            val originLat = header.getDouble()
            val originLon = header.getDouble()
            val noDataValue = header.getFloat()
            return DemTileReader(
                file = raf,
                width = width,
                height = height,
                tileLat = originLat.toInt(),
                tileLon = originLon.toInt(),
                noDataValue = noDataValue
            )
        }
    }
}
