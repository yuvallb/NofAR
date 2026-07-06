package com.nofar.core.data.dem

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writes Prepare-time DEM tiles as little-endian float32 row-major grids (north-up).
 */
class DemTileWriter(
    private val tileLat: Int,
    private val tileLon: Int,
    private val noDataValue: Float = DemBinaryFormat.DEFAULT_NO_DATA_VALUE
) {
    fun write(outputFile: File, width: Int, height: Int, elevations: FloatArray) {
        require(elevations.size == width * height) {
            "Expected ${width * height} samples, got ${elevations.size}"
        }
        outputFile.parentFile?.mkdirs()
        val totalBytes = DemBinaryFormat.HEADER_SIZE_BYTES + elevations.size * Float.SIZE_BYTES
        val buffer = ByteBuffer.allocate(totalBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(DemBinaryFormat.MAGIC.toByteArray(Charsets.US_ASCII))
        buffer.putInt(width)
        buffer.putInt(height)
        buffer.putDouble(tileLat.toDouble())
        buffer.putDouble(tileLon.toDouble())
        buffer.putFloat(noDataValue)
        buffer.position(DemBinaryFormat.HEADER_SIZE_BYTES)
        buffer.asFloatBuffer().put(elevations)

        RandomAccessFile(outputFile, "rw").use { file ->
            file.setLength(0)
            file.write(buffer.array())
        }
    }
}
