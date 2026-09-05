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
    val noDataValue: Float,
    private val scale: Float,
    private val valueOffset: Float
) : Closeable {
    override fun close() {
        file.close()
    }

    fun elevationAt(lat: Double, lon: Double): Float? {
        if (!isInsideTile(lat, lon)) return null

        val x = pixelX(lon)
        val y = pixelY(lat)
        return decodeSample(x, y)
    }

    /** Bilinear sample for map viewshed preview (not used on Explore hot path). */
    fun elevationAtBilinear(lat: Double, lon: Double): Float? =
        if (isInsideTile(lat, lon)) interpolateBilinear(lat, lon) else null

    private fun interpolateBilinear(lat: Double, lon: Double): Float? {
        val lon0 = tileLon.toDouble()
        val lon1 = tileLon + 1.0
        val lat0 = tileLat.toDouble()
        val lat1 = tileLat + 1.0
        val xFrac = ((lon - lon0) / (lon1 - lon0) * width - 0.5).coerceIn(0.0, (width - 1).toDouble())
        val yFrac = ((lat1 - lat) / (lat1 - lat0) * height - 0.5).coerceIn(0.0, (height - 1).toDouble())
        val x0 = floor(xFrac).toInt().coerceIn(0, width - 1)
        val y0 = floor(yFrac).toInt().coerceIn(0, height - 1)
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)
        val tx = (xFrac - x0).toFloat()
        val ty = (yFrac - y0).toFloat()
        val v00 = decodeSample(x0, y0)
        val v10 = decodeSample(x1, y0)
        val v01 = decodeSample(x0, y1)
        val v11 = decodeSample(x1, y1)
        val cornersValid = v00 != null && v10 != null && v01 != null && v11 != null
        return if (cornersValid) {
            val top = v00!! * (1f - tx) + v10 * tx
            val bottom = v01!! * (1f - tx) + v11 * tx
            top * (1f - ty) + bottom * ty
        } else {
            null
        }
    }

    private fun decodeSample(x: Int, y: Int): Float? {
        val offset = y * width + x
        val raw = dataBuffer.getShort(offset * DemBinaryFormat.BYTES_PER_SAMPLE)
        if (raw == DemBinaryFormat.INT16_NO_DATA) return null
        val value = raw * scale + valueOffset
        return if (isPlausibleElevation(value)) value else null
    }

    /**
     * Guards against no-data sentinels that never got normalised to [noDataValue] — notably Copernicus
     * GLO-30's `-32767`, which older tiles kept verbatim because the converter assumed `-9999`.
     *
     * Treating a sentinel as real elevation is not a cosmetic error: the Explore skyline derives the
     * observer eye from the DEM pixel under the user, so one sentinel there put the eye ~32 km
     * underground and made every azimuth's nearest sample read as ~90° — a horizon line that tracked
     * camera pitch instead of terrain. Bounds are deliberately wide (Dead Sea floor to above Everest).
     */
    private fun isPlausibleElevation(value: Float): Boolean = value.isFinite() &&
        value != noDataValue &&
        value >= MIN_PLAUSIBLE_ELEVATION_M &&
        value <= MAX_PLAUSIBLE_ELEVATION_M

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
        val frac = ((lon - lon0) / (lon1 - lon0)).coerceIn(0.0, 1.0 - 1e-12)
        return floor(frac * width).toInt().coerceIn(0, width - 1)
    }

    private fun pixelY(lat: Double): Int {
        val lat0 = tileLat.toDouble()
        val lat1 = tileLat + 1.0
        val frac = ((lat1 - lat) / (lat1 - lat0)).coerceIn(0.0, 1.0 - 1e-12)
        return floor(frac * height).toInt().coerceIn(0, height - 1)
    }

    companion object {
        private const val MAX_RASTER_DIMENSION = 10_000

        /** Below the Dead Sea floor (~-730 m); rejects `-9999` / `-32767` sentinels. */
        private const val MIN_PLAUSIBLE_ELEVATION_M = -1_000f

        /** Above Everest (8849 m); rejects `32767` and similar positive sentinels. */
        private const val MAX_PLAUSIBLE_ELEVATION_M = 9_000f

        fun hasCurrentFormat(file: File): Boolean = readMagic(file) == DemBinaryFormat.MAGIC

        private fun readMagic(file: File): String? {
            if (!file.exists() || file.length() <= DemBinaryFormat.HEADER_SIZE_BYTES) return null
            return runCatching {
                RandomAccessFile(file, "r").use { input ->
                    val magicBytes = ByteArray(DemBinaryFormat.MAGIC_SIZE_BYTES)
                    input.readFully(magicBytes)
                    magicBytes.toString(Charsets.US_ASCII)
                }
            }.getOrNull()
        }

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
            val sampleType = header.getInt()
            require(sampleType == DemBinaryFormat.SAMPLE_TYPE_INT16) {
                "Unsupported DEM sample type: $sampleType"
            }
            val scale = header.getFloat()
            val valueOffset = header.getFloat()
            val noDataValue = header.getFloat()
            val dataBytes = width.toLong() * height * DemBinaryFormat.BYTES_PER_SAMPLE
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
                noDataValue = noDataValue,
                scale = scale,
                valueOffset = valueOffset
            )
        }
    }
}
