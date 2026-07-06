package com.nofar.core.data.dem

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

interface GeoTiffConverter {
    fun convert(inputFile: File, tileLat: Int, tileLon: Int, outputFile: File): GeoTiffConversionResult
}

data class GeoTiffConversionResult(val width: Int, val height: Int, val noDataValue: Float, val sizeBytes: Long)

/**
 * Minimal strip-based GeoTIFF reader for single-band float32 Copernicus DEM tiles.
 * Prepare-only — never invoked from Explore.
 */
class DefaultGeoTiffConverter : GeoTiffConverter {
    override fun convert(inputFile: File, tileLat: Int, tileLon: Int, outputFile: File): GeoTiffConversionResult {
        val bytes = inputFile.readBytes()
        val width = readInt(bytes, findTag(bytes, TAG_IMAGE_WIDTH))
        val height = readInt(bytes, findTag(bytes, TAG_IMAGE_LENGTH))
        val bitsPerSample = readShort(bytes, findTag(bytes, TAG_BITS_PER_SAMPLE)).toInt()
        require(bitsPerSample == 32) { "Expected 32-bit samples, got $bitsPerSample" }

        val stripOffsets = readIntArray(bytes, findTag(bytes, TAG_STRIP_OFFSETS))
        val stripByteCounts = readIntArray(bytes, findTag(bytes, TAG_STRIP_BYTE_COUNTS))
        val elevations = FloatArray(width * height)
        var row = 0
        for (i in stripOffsets.indices) {
            val offset = stripOffsets[i]
            val count = stripByteCounts[i]
            val stripBuffer = ByteBuffer.wrap(bytes, offset, count).order(ByteOrder.LITTLE_ENDIAN)
            val floatsInStrip = count / Float.SIZE_BYTES
            for (j in 0 until floatsInStrip) {
                val index = row * width + j
                if (index < elevations.size) {
                    elevations[index] = stripBuffer.float
                }
            }
            row++
        }

        val writer = DemTileWriter(tileLat = tileLat, tileLon = tileLon)
        writer.write(outputFile, width, height, elevations)
        return GeoTiffConversionResult(
            width = width,
            height = height,
            noDataValue = DemBinaryFormat.DEFAULT_NO_DATA_VALUE,
            sizeBytes = outputFile.length()
        )
    }

    private fun findTag(bytes: ByteArray, tag: Int): Int {
        val littleEndian = bytes[0] == 'I'.code.toByte() && bytes[1] == 'I'.code.toByte()
        val order = if (littleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
        val ifdOffset = readInt(bytes, 4, order)
        val entryCount = readShort(bytes, ifdOffset, order).toInt()
        var offset = ifdOffset + 2
        repeat(entryCount) {
            val currentTag = readShort(bytes, offset, order).toInt()
            if (currentTag == tag) return offset + 8
            offset += 12
        }
        throw IOException("TIFF tag $tag not found")
    }

    private fun readInt(bytes: ByteArray, offset: Int, order: ByteOrder = ByteOrder.LITTLE_ENDIAN): Int =
        ByteBuffer.wrap(bytes, offset, 4).order(order).int

    private fun readShort(bytes: ByteArray, offset: Int, order: ByteOrder = ByteOrder.LITTLE_ENDIAN): Short =
        ByteBuffer.wrap(bytes, offset, 2).order(order).short

    private fun readIntArray(bytes: ByteArray, valueOffset: Int): IntArray {
        val countOffset = valueOffset - 4
        val count = readShort(bytes, countOffset).toInt()
        val dataOffset = readInt(bytes, valueOffset)
        return IntArray(count) { index ->
            readInt(bytes, dataOffset + index * 4)
        }
    }

    companion object {
        private const val TAG_IMAGE_WIDTH = 256
        private const val TAG_IMAGE_LENGTH = 257
        private const val TAG_BITS_PER_SAMPLE = 258
        private const val TAG_STRIP_OFFSETS = 273
        private const val TAG_STRIP_BYTE_COUNTS = 279
    }
}
