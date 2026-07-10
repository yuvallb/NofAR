package com.nofar.core.data.dem

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

interface GeoTiffConverter {
    fun convert(inputFile: File, tileLat: Int, tileLon: Int, outputFile: File): GeoTiffConversionResult
}

data class GeoTiffConversionResult(val width: Int, val height: Int, val noDataValue: Float, val sizeBytes: Long)

/**
 * Minimal GeoTIFF reader for single-band float32 Copernicus DEM COG tiles.
 * Supports tiled DEFLATE rasters (the on-disk format used by Copernicus GLO-30)
 * and uncompressed strip layouts for tests. Prepare-only — never invoked from Explore.
 */
class DefaultGeoTiffConverter : GeoTiffConverter {
    override fun convert(inputFile: File, tileLat: Int, tileLon: Int, outputFile: File): GeoTiffConversionResult {
        val bytes = inputFile.readBytes()
        val directory = TiffDirectory.parse(bytes)
        require(directory.bitsPerSample == 32) {
            "Expected 32-bit samples, got ${directory.bitsPerSample}"
        }
        require(directory.sampleFormat == SAMPLE_FORMAT_FLOAT) {
            "Expected IEEE float samples, got format ${directory.sampleFormat}"
        }

        val elevations =
            when {
                directory.tileOffsets != null && directory.tileByteCounts != null ->
                    readTiledFloatRaster(bytes, directory)
                directory.stripOffsets != null && directory.stripByteCounts != null ->
                    readStripFloatRaster(bytes, directory)
                else -> throw IOException("GeoTIFF has neither tile nor strip offsets")
            }

        val writer = DemTileWriter(tileLat = tileLat, tileLon = tileLon)
        writer.write(outputFile, directory.width, directory.height, elevations)
        return GeoTiffConversionResult(
            width = directory.width,
            height = directory.height,
            noDataValue = DemBinaryFormat.DEFAULT_NO_DATA_VALUE,
            sizeBytes = outputFile.length()
        )
    }

    private fun readTiledFloatRaster(bytes: ByteArray, directory: TiffDirectory): FloatArray {
        val tileOffsets = directory.tileOffsets ?: throw IOException("Missing TileOffsets")
        val tileByteCounts = directory.tileByteCounts ?: throw IOException("Missing TileByteCounts")
        require(tileOffsets.size == tileByteCounts.size) { "Tile offset/count mismatch" }

        val elevations = FloatArray(directory.width * directory.height)
        val tilesAcross = (directory.width + directory.tileWidth - 1) / directory.tileWidth

        tileOffsets.indices.forEach { index ->
            val tileX = index % tilesAcross
            val tileY = index / tilesAcross
            val startX = tileX * directory.tileWidth
            val startY = tileY * directory.tileLength
            val tilePixelWidth = minOf(directory.tileWidth, directory.width - startX)
            val tilePixelHeight = minOf(directory.tileLength, directory.height - startY)
            val compressed = bytes.copyOfRange(tileOffsets[index], tileOffsets[index] + tileByteCounts[index])
            val tileSamples =
                decodeCompressedSamples(
                    compressed = compressed,
                    compression = directory.compression,
                    sampleCount = tilePixelWidth * tilePixelHeight,
                    byteOrder = directory.byteOrder
                )
            copyTileIntoRaster(
                elevations = elevations,
                rasterWidth = directory.width,
                startX = startX,
                startY = startY,
                tilePixelWidth = tilePixelWidth,
                tilePixelHeight = tilePixelHeight,
                tileStride = tilePixelWidth,
                tileSamples = tileSamples
            )
        }
        return elevations
    }

    private fun readStripFloatRaster(bytes: ByteArray, directory: TiffDirectory): FloatArray {
        val stripOffsets = directory.stripOffsets ?: throw IOException("Missing StripOffsets")
        val stripByteCounts = directory.stripByteCounts ?: throw IOException("Missing StripByteCounts")
        require(stripOffsets.size == stripByteCounts.size) { "Strip offset/count mismatch" }

        val elevations = FloatArray(directory.width * directory.height)
        var row = 0
        stripOffsets.indices.forEach { index ->
            val compressed = bytes.copyOfRange(stripOffsets[index], stripOffsets[index] + stripByteCounts[index])
            val rowsInStrip = minOf(directory.rowsPerStrip, directory.height - row)
            val sampleCount = directory.width * rowsInStrip
            val stripSamples =
                decodeCompressedSamples(
                    compressed = compressed,
                    compression = directory.compression,
                    sampleCount = sampleCount,
                    byteOrder = directory.byteOrder
                )
            for (stripRow in 0 until rowsInStrip) {
                val dstRow = row + stripRow
                for (col in 0 until directory.width) {
                    elevations[dstRow * directory.width + col] = stripSamples[stripRow * directory.width + col]
                }
            }
            row += rowsInStrip
        }
        return elevations
    }

    private fun decodeCompressedSamples(
        compressed: ByteArray,
        compression: Int,
        sampleCount: Int,
        byteOrder: ByteOrder
    ): FloatArray {
        val raw =
            when (compression) {
                COMPRESSION_NONE -> compressed
                COMPRESSION_DEFLATE, COMPRESSION_ADOBE_DEFLATE -> inflateZlib(compressed)
                else -> throw IOException("Unsupported TIFF compression: $compression")
            }
        val expectedBytes = sampleCount * Float.SIZE_BYTES
        require(raw.size >= expectedBytes) {
            "Expected at least $expectedBytes decoded bytes, got ${raw.size}"
        }
        val buffer = ByteBuffer.wrap(raw).order(byteOrder)
        return FloatArray(sampleCount) { buffer.float }
    }

    private fun copyTileIntoRaster(
        elevations: FloatArray,
        rasterWidth: Int,
        startX: Int,
        startY: Int,
        tilePixelWidth: Int,
        tilePixelHeight: Int,
        tileStride: Int,
        tileSamples: FloatArray
    ) {
        for (row in 0 until tilePixelHeight) {
            for (col in 0 until tilePixelWidth) {
                elevations[(startY + row) * rasterWidth + (startX + col)] = tileSamples[row * tileStride + col]
            }
        }
    }

    private fun inflateZlib(compressed: ByteArray): ByteArray {
        val inflater = Inflater()
        return try {
            inflater.setInput(compressed)
            val output = ByteArrayOutputStream(compressed.size * 2)
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) {
                    if (inflater.needsInput()) {
                        throw IOException("Unexpected end of DEFLATE stream")
                    }
                    break
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } finally {
            inflater.end()
        }
    }

    private data class TiffDirectory(
        val byteOrder: ByteOrder,
        val width: Int,
        val height: Int,
        val bitsPerSample: Int,
        val sampleFormat: Int,
        val compression: Int,
        val rowsPerStrip: Int,
        val tileWidth: Int,
        val tileLength: Int,
        val stripOffsets: IntArray?,
        val stripByteCounts: IntArray?,
        val tileOffsets: IntArray?,
        val tileByteCounts: IntArray?
    ) {
        companion object {
            fun parse(bytes: ByteArray): TiffDirectory {
                val byteOrder =
                    when {
                        bytes.size >= 2 && bytes[0] == 'I'.code.toByte() && bytes[1] == 'I'.code.toByte() ->
                            ByteOrder.LITTLE_ENDIAN
                        bytes.size >= 2 && bytes[0] == 'M'.code.toByte() && bytes[1] == 'M'.code.toByte() ->
                            ByteOrder.BIG_ENDIAN
                        else -> throw IOException("Invalid TIFF byte order")
                    }
                val ifdOffset = readInt(bytes, byteOrder, 4)
                return parseIfd(bytes, byteOrder, ifdOffset)
            }

            private fun parseIfd(bytes: ByteArray, byteOrder: ByteOrder, ifdOffset: Int): TiffDirectory {
                val entryCount = readShort(bytes, ifdOffset, byteOrder).toInt()
                var width = 0
                var height = 0
                var bitsPerSample = 32
                var sampleFormat = SAMPLE_FORMAT_FLOAT
                var compression = COMPRESSION_NONE
                var rowsPerStrip = Int.MAX_VALUE
                var tileWidth = 0
                var tileLength = 0
                var stripOffsets: IntArray? = null
                var stripByteCounts: IntArray? = null
                var tileOffsets: IntArray? = null
                var tileByteCounts: IntArray? = null

                repeat(entryCount) { index ->
                    val entryOffset = ifdOffset + 2 + index * 12
                    val tag = readShort(bytes, entryOffset, byteOrder).toInt()
                    val type = readShort(bytes, entryOffset + 2, byteOrder).toInt()
                    val count = readInt(bytes, byteOrder, entryOffset + 4)
                    when (tag) {
                        TAG_IMAGE_WIDTH -> width = readTagScalar(bytes, byteOrder, entryOffset, type).toInt()
                        TAG_IMAGE_LENGTH -> height = readTagScalar(bytes, byteOrder, entryOffset, type).toInt()
                        TAG_BITS_PER_SAMPLE -> bitsPerSample = readTagScalar(bytes, byteOrder, entryOffset, type).toInt()
                        TAG_SAMPLE_FORMAT -> sampleFormat = readTagScalar(bytes, byteOrder, entryOffset, type).toInt()
                        TAG_COMPRESSION -> compression = readTagScalar(bytes, byteOrder, entryOffset, type).toInt()
                        TAG_ROWS_PER_STRIP -> rowsPerStrip = readTagScalar(bytes, byteOrder, entryOffset, type).toInt()
                        TAG_TILE_WIDTH -> tileWidth = readTagScalar(bytes, byteOrder, entryOffset, type).toInt()
                        TAG_TILE_LENGTH -> tileLength = readTagScalar(bytes, byteOrder, entryOffset, type).toInt()
                        TAG_STRIP_OFFSETS -> stripOffsets = readTagIntArray(bytes, byteOrder, entryOffset, type, count)
                        TAG_STRIP_BYTE_COUNTS -> stripByteCounts = readTagIntArray(bytes, byteOrder, entryOffset, type, count)
                        TAG_TILE_OFFSETS -> tileOffsets = readTagIntArray(bytes, byteOrder, entryOffset, type, count)
                        TAG_TILE_BYTE_COUNTS -> tileByteCounts = readTagIntArray(bytes, byteOrder, entryOffset, type, count)
                    }
                }

                require(width > 0 && height > 0) { "Missing image dimensions" }
                if (rowsPerStrip == Int.MAX_VALUE) {
                    rowsPerStrip = height
                }
                return TiffDirectory(
                    byteOrder = byteOrder,
                    width = width,
                    height = height,
                    bitsPerSample = bitsPerSample,
                    sampleFormat = sampleFormat,
                    compression = compression,
                    rowsPerStrip = rowsPerStrip,
                    tileWidth = tileWidth,
                    tileLength = tileLength,
                    stripOffsets = stripOffsets,
                    stripByteCounts = stripByteCounts,
                    tileOffsets = tileOffsets,
                    tileByteCounts = tileByteCounts
                )
            }

            private fun readTagScalar(bytes: ByteArray, byteOrder: ByteOrder, entryOffset: Int, type: Int): Long {
                val valueOffset = entryOffset + 8
                return when (type) {
                    TYPE_SHORT -> readShort(bytes, valueOffset, byteOrder).toLong() and 0xFFFF
                    TYPE_LONG -> readInt(bytes, byteOrder, valueOffset).toLong() and 0xFFFF_FFFFL
                    else -> throw IOException("Unsupported TIFF scalar type: $type")
                }
            }

            private fun readTagIntArray(
                bytes: ByteArray,
                byteOrder: ByteOrder,
                entryOffset: Int,
                type: Int,
                count: Int
            ): IntArray {
                val valueField = entryOffset + 8
                val dataOffset =
                    if (type == TYPE_SHORT) {
                        val arrayBytes = count * 2
                        if (arrayBytes <= 4) valueField else readInt(bytes, byteOrder, valueField)
                    } else if (type == TYPE_LONG) {
                        val arrayBytes = count * 4
                        if (arrayBytes <= 4) valueField else readInt(bytes, byteOrder, valueField)
                    } else {
                        throw IOException("Unsupported TIFF array type: $type")
                    }

                return IntArray(count) { index ->
                    when (type) {
                        TYPE_SHORT -> readShort(bytes, dataOffset + index * 2, byteOrder).toInt() and 0xFFFF
                        TYPE_LONG -> readInt(bytes, byteOrder, dataOffset + index * 4)
                        else -> error("unreachable")
                    }
                }
            }

            private fun readShort(bytes: ByteArray, offset: Int, order: ByteOrder): Short =
                ByteBuffer.wrap(bytes, offset, 2).order(order).short

            private fun readInt(bytes: ByteArray, order: ByteOrder, offset: Int): Int =
                ByteBuffer.wrap(bytes, offset, 4).order(order).int
        }
    }

    companion object {
        private const val SAMPLE_FORMAT_FLOAT = 3
        private const val COMPRESSION_NONE = 1
        private const val COMPRESSION_DEFLATE = 8
        private const val COMPRESSION_ADOBE_DEFLATE = 32946

        private const val TAG_IMAGE_WIDTH = 256
        private const val TAG_IMAGE_LENGTH = 257
        private const val TAG_BITS_PER_SAMPLE = 258
        private const val TAG_COMPRESSION = 259
        private const val TAG_ROWS_PER_STRIP = 278
        private const val TAG_STRIP_OFFSETS = 273
        private const val TAG_STRIP_BYTE_COUNTS = 279
        private const val TAG_TILE_WIDTH = 322
        private const val TAG_TILE_LENGTH = 323
        private const val TAG_TILE_OFFSETS = 324
        private const val TAG_TILE_BYTE_COUNTS = 325
        private const val TAG_SAMPLE_FORMAT = 339

        private const val TYPE_SHORT = 3
        private const val TYPE_LONG = 4
    }
}
