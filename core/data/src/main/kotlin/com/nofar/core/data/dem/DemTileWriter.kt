@file:Suppress("ReturnCount")

package com.nofar.core.data.dem

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Writes Prepare-time DEM tiles as little-endian int16 meters (north-up), memory-mapped at Explore time.
 */
class DemTileWriter(private val tileLat: Int, private val tileLon: Int) {
    fun write(outputFile: File, width: Int, height: Int, elevations: FloatArray) {
        require(elevations.size == width * height) {
            "Expected ${width * height} samples, got ${elevations.size}"
        }
        outputFile.parentFile?.mkdirs()
        val samples = ShortArray(elevations.size) { index -> quantize(elevations[index]) }
        val totalBytes = DemBinaryFormat.HEADER_SIZE_BYTES + samples.size * DemBinaryFormat.BYTES_PER_SAMPLE
        val buffer = ByteBuffer.allocate(totalBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(DemBinaryFormat.MAGIC.toByteArray(Charsets.US_ASCII))
        buffer.putInt(width)
        buffer.putInt(height)
        buffer.putDouble(tileLat.toDouble())
        buffer.putDouble(tileLon.toDouble())
        buffer.putInt(DemBinaryFormat.SAMPLE_TYPE_INT16)
        buffer.putFloat(DemBinaryFormat.SCALE)
        buffer.putFloat(DemBinaryFormat.OFFSET)
        buffer.putFloat(DemBinaryFormat.HEADER_NO_DATA_VALUE)
        buffer.position(DemBinaryFormat.HEADER_SIZE_BYTES)
        buffer.asShortBuffer().put(samples)

        RandomAccessFile(outputFile, "rw").use { file ->
            file.setLength(0)
            file.write(buffer.array())
        }
    }

    private fun quantize(value: Float): Short {
        if (!value.isFinite() || value == DemBinaryFormat.HEADER_NO_DATA_VALUE) {
            return DemBinaryFormat.INT16_NO_DATA
        }
        val rounded = value.roundToInt()
        if (rounded < DemBinaryFormat.MIN_QUANTIZED_ELEVATION_M ||
            rounded > DemBinaryFormat.MAX_QUANTIZED_ELEVATION_M
        ) {
            return DemBinaryFormat.INT16_NO_DATA
        }
        return rounded.toShort()
    }
}
