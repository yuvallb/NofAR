package com.nofar.core.data.dem

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.floor

/**
 * Explore-time random-access elevation lookup from a converted `.bin` tile.
 *
 * Elevation raster is memory-mapped for zero-copy lookup in the visibility hot loop.
 */
class DemTileReader private constructor(
    private val file: RandomAccessFile,
    private val dataBuffer: MappedByteBuffer,
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
        val offset = y * width + x
        val value = dataBuffer.getFloat(offset * Float.SIZE_BYTES)
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
        private const val MAX_RASTER_DIMENSION = 10_000

        fun open(file: File): DemTileReader {
            val raf = RandomAccessFile(file, "r")
            val headerBytes = ByteArray(DemBinaryFormat.HEADER_SIZE_BYTES)
            raf.readFully(headerBytes)
            val header = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = CharArray(DemBinaryFormat.MAGIC_SIZE_BYTES) { header.get().toInt().toChar() }.concatToString()
            require(magic == DemBinaryFormat.MAGIC) { "Invalid DEM magic: $magic" }
            val width = header.getInt()
            val height = header.getInt()
            require(width in 1..MAX_RASTER_DIMENSION && height in 1..MAX_RASTER_DIMENSION) {
                "Invalid DEM dimensions: ${width}x$height"
            }
            val originLat = header.getDouble()
            val originLon = header.getDouble()
            val noDataValue = header.getFloat()
            val dataBytes = width.toLong() * height * Float.SIZE_BYTES
            val mapped =
                raf.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    DemBinaryFormat.HEADER_SIZE_BYTES.toLong(),
                    dataBytes
                )
            mapped.order(ByteOrder.LITTLE_ENDIAN)
            return DemTileReader(
                file = raf,
                dataBuffer = mapped,
                width = width,
                height = height,
                tileLat = originLat.toInt(),
                tileLon = originLon.toInt(),
                noDataValue = noDataValue
            )
        }
    }
}
