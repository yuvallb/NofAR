package com.nofar.core.data.dem

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
        require(inputFile.length() <= MAX_INPUT_BYTES) {
            "GeoTIFF exceeds size limit (${inputFile.length()} > $MAX_INPUT_BYTES bytes)"
        }
        val bytes = inputFile.readBytes()
        val directory = TiffDirectory.parse(bytes)
        require(directory.width in 1..MAX_RASTER_DIMENSION && directory.height in 1..MAX_RASTER_DIMENSION) {
            "GeoTIFF dimensions ${directory.width}x${directory.height} outside 1..$MAX_RASTER_DIMENSION"
        }
        require(directory.bitsPerSample == 32) {
            "Expected 32-bit samples, got ${directory.bitsPerSample}"
        }
        require(directory.sampleFormat == SAMPLE_FORMAT_FLOAT) {
            "Expected IEEE float samples, got format ${directory.sampleFormat}"
        }
        require(directory.predictor == PREDICTOR_NONE || directory.predictor == PREDICTOR_FLOATING_POINT) {
            "Unsupported TIFF predictor: ${directory.predictor}"
        }

        val elevations =
            when {
                directory.tileOffsets != null && directory.tileByteCounts != null ->
                    readTiledFloatRaster(bytes, directory)
                directory.stripOffsets != null && directory.stripByteCounts != null ->
                    readStripFloatRaster(bytes, directory)
                else -> throw IOException("GeoTIFF has neither tile nor strip offsets")
            }

        normalizeNoData(elevations, directory.noDataValue)

        val writer = DemTileWriter(tileLat = tileLat, tileLon = tileLon)
        writer.write(outputFile, directory.width, directory.height, elevations)
        return GeoTiffConversionResult(
            width = directory.width,
            height = directory.height,
            noDataValue = DemBinaryFormat.DEFAULT_NO_DATA_VALUE,
            sizeBytes = outputFile.length()
        )
    }

    /**
     * Rewrites the source raster's own no-data sentinel (GDAL_NODATA, e.g. Copernicus GLO-30's
     * `-32767`) to [DemBinaryFormat.DEFAULT_NO_DATA_VALUE], which is what the `.bin` header declares.
     *
     * Without this the sentinel survived into Explore as a real elevation, and because the skyline eye
     * comes from the DEM pixel under the observer it produced a near-vertical horizon.
     */
    private fun normalizeNoData(elevations: FloatArray, sourceNoDataValue: Float?) {
        val canonical = DemBinaryFormat.DEFAULT_NO_DATA_VALUE
        for (index in elevations.indices) {
            val value = elevations[index]
            val isNoData = !value.isFinite() || (sourceNoDataValue != null && value == sourceNoDataValue)
            if (isNoData) {
                elevations[index] = canonical
            }
        }
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
                    // TIFF stores every tile at its declared dimensions. Edge tiles are padded;
                    // their valid image area is smaller, but their row stride is still tileWidth.
                    sampleCount = directory.tileWidth * directory.tileLength,
                    rowSampleCount = directory.tileWidth,
                    predictor = directory.predictor,
                    byteOrder = directory.byteOrder
                )
            copyTileIntoRaster(
                elevations = elevations,
                rasterWidth = directory.width,
                startX = startX,
                startY = startY,
                tilePixelWidth = tilePixelWidth,
                tilePixelHeight = tilePixelHeight,
                tileStride = directory.tileWidth,
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
                    rowSampleCount = directory.width,
                    predictor = directory.predictor,
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
        rowSampleCount: Int,
        predictor: Int,
        byteOrder: ByteOrder
    ): FloatArray {
        val decodedBytes =
            when (compression) {
                COMPRESSION_NONE -> compressed
                COMPRESSION_DEFLATE, COMPRESSION_ADOBE_DEFLATE ->
                    inflateZlib(compressed, maxDecodedBytes = sampleCount * Float.SIZE_BYTES)
                else -> throw IOException("Unsupported TIFF compression: $compression")
            }
        val expectedBytes = sampleCount * Float.SIZE_BYTES
        require(decodedBytes.size >= expectedBytes) {
            "Expected at least $expectedBytes decoded bytes, got ${decodedBytes.size}"
        }
        if (predictor == PREDICTOR_FLOATING_POINT) {
            decodeFloatingPointPredictor(
                bytes = decodedBytes,
                byteCount = expectedBytes,
                rowSampleCount = rowSampleCount,
                byteOrder = byteOrder
            )
        }
        val buffer = ByteBuffer.wrap(decodedBytes).order(byteOrder)
        return FloatArray(sampleCount) { buffer.float }
    }

    /**
     * Reverses TIFF Predictor=3 for one-band float32 rows.
     *
     * The predictor stores significance-byte planes, then applies horizontal byte differencing to
     * each complete row. Accumulation must happen before the planes are interleaved back into floats.
     */
    private fun decodeFloatingPointPredictor(
        bytes: ByteArray,
        byteCount: Int,
        rowSampleCount: Int,
        byteOrder: ByteOrder
    ) {
        val bytesPerSample = Float.SIZE_BYTES
        val rowByteCount = rowSampleCount * bytesPerSample
        require(rowByteCount > 0 && byteCount % rowByteCount == 0) {
            "Predictor byte count $byteCount is not a whole number of $rowByteCount-byte rows"
        }
        val rowBuffer = ByteArray(rowByteCount)
        var rowOffset = 0
        while (rowOffset < byteCount) {
            for (index in 1 until rowByteCount) {
                val accumulated = (bytes[rowOffset + index].toInt() and 0xFF) +
                    (bytes[rowOffset + index - 1].toInt() and 0xFF)
                bytes[rowOffset + index] = accumulated.toByte()
            }
            bytes.copyInto(rowBuffer, startIndex = rowOffset, endIndex = rowOffset + rowByteCount)
            for (sampleIndex in 0 until rowSampleCount) {
                for (byteIndex in 0 until bytesPerSample) {
                    val planeIndex =
                        if (byteOrder == ByteOrder.BIG_ENDIAN) {
                            byteIndex
                        } else {
                            bytesPerSample - byteIndex - 1
                        }
                    bytes[rowOffset + sampleIndex * bytesPerSample + byteIndex] =
                        rowBuffer[planeIndex * rowSampleCount + sampleIndex]
                }
            }
            rowOffset += rowByteCount
        }
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

    private fun inflateZlib(compressed: ByteArray, maxDecodedBytes: Int): ByteArray {
        val inflater = Inflater()
        return try {
            inflater.setInput(compressed)
            val output = ByteArrayOutputStream(minOf(compressed.size * 2, maxDecodedBytes))
            val buffer = ByteArray(8192)
            var decoded = 0
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) {
                    if (inflater.needsInput()) {
                        throw IOException("Unexpected end of DEFLATE stream")
                    }
                    break
                }
                decoded += count
                if (decoded > maxDecodedBytes) {
                    throw IOException(
                        "DEFLATE output exceeds size limit ($decoded > $maxDecodedBytes bytes)"
                    )
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
        val predictor: Int,
        val rowsPerStrip: Int,
        val tileWidth: Int,
        val tileLength: Int,
        val stripOffsets: IntArray?,
        val stripByteCounts: IntArray?,
        val tileOffsets: IntArray?,
        val tileByteCounts: IntArray?,
        val noDataValue: Float?
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
                val state = IfdParseState()

                repeat(entryCount) { index ->
                    val entryOffset = ifdOffset + 2 + index * 12
                    state.applyEntry(bytes, byteOrder, entryOffset)
                }

                require(state.width > 0 && state.height > 0) { "Missing image dimensions" }
                if (state.rowsPerStrip == Int.MAX_VALUE) {
                    state.rowsPerStrip = state.height
                }
                return state.toDirectory(byteOrder)
            }

            private class IfdParseState {
                var width = 0
                var height = 0
                var bitsPerSample = 32
                var sampleFormat = SAMPLE_FORMAT_FLOAT
                var compression = COMPRESSION_NONE
                var predictor = PREDICTOR_NONE
                var rowsPerStrip = Int.MAX_VALUE
                var tileWidth = 0
                var tileLength = 0
                var stripOffsets: IntArray? = null
                var stripByteCounts: IntArray? = null
                var tileOffsets: IntArray? = null
                var tileByteCounts: IntArray? = null
                var noDataValue: Float? = null

                fun applyEntry(bytes: ByteArray, byteOrder: ByteOrder, entryOffset: Int) {
                    val tag = readShort(bytes, entryOffset, byteOrder).toInt()
                    val type = readShort(bytes, entryOffset + 2, byteOrder).toInt()
                    val count = readInt(bytes, byteOrder, entryOffset + 4)
                    applyTag(bytes, byteOrder, entryOffset, tag, type, count)
                }

                fun applyTag(
                    bytes: ByteArray,
                    byteOrder: ByteOrder,
                    entryOffset: Int,
                    tag: Int,
                    type: Int,
                    count: Int
                ) {
                    when (tag) {
                        TAG_IMAGE_WIDTH -> width = readTagScalarInt(bytes, byteOrder, entryOffset, type)
                        TAG_IMAGE_LENGTH -> height = readTagScalarInt(bytes, byteOrder, entryOffset, type)
                        TAG_BITS_PER_SAMPLE -> bitsPerSample = readTagScalarInt(bytes, byteOrder, entryOffset, type)
                        TAG_SAMPLE_FORMAT -> sampleFormat = readTagScalarInt(bytes, byteOrder, entryOffset, type)
                        TAG_COMPRESSION, TAG_PREDICTOR ->
                            applyEncodingTag(bytes, byteOrder, entryOffset, tag, type)
                        TAG_ROWS_PER_STRIP -> rowsPerStrip = readTagScalarInt(bytes, byteOrder, entryOffset, type)
                        TAG_TILE_WIDTH -> tileWidth = readTagScalarInt(bytes, byteOrder, entryOffset, type)
                        TAG_TILE_LENGTH -> tileLength = readTagScalarInt(bytes, byteOrder, entryOffset, type)
                        TAG_STRIP_OFFSETS ->
                            stripOffsets = readTagIntArray(bytes, byteOrder, entryOffset, type, count)
                        TAG_STRIP_BYTE_COUNTS ->
                            stripByteCounts = readTagIntArray(bytes, byteOrder, entryOffset, type, count)
                        TAG_TILE_OFFSETS ->
                            tileOffsets = readTagIntArray(bytes, byteOrder, entryOffset, type, count)
                        TAG_TILE_BYTE_COUNTS ->
                            tileByteCounts = readTagIntArray(bytes, byteOrder, entryOffset, type, count)
                        TAG_GDAL_NODATA ->
                            noDataValue = readTagAsciiFloat(bytes, byteOrder, entryOffset, type, count)
                    }
                }

                private fun applyEncodingTag(
                    bytes: ByteArray,
                    byteOrder: ByteOrder,
                    entryOffset: Int,
                    tag: Int,
                    type: Int
                ) {
                    val value = readTagScalarInt(bytes, byteOrder, entryOffset, type)
                    when (tag) {
                        TAG_COMPRESSION -> compression = value
                        TAG_PREDICTOR -> predictor = value
                    }
                }

                fun toDirectory(byteOrder: ByteOrder): TiffDirectory = TiffDirectory(
                    byteOrder = byteOrder,
                    width = width,
                    height = height,
                    bitsPerSample = bitsPerSample,
                    sampleFormat = sampleFormat,
                    compression = compression,
                    predictor = predictor,
                    rowsPerStrip = rowsPerStrip,
                    tileWidth = tileWidth,
                    tileLength = tileLength,
                    stripOffsets = stripOffsets,
                    stripByteCounts = stripByteCounts,
                    tileOffsets = tileOffsets,
                    tileByteCounts = tileByteCounts,
                    noDataValue = noDataValue
                )
            }

            private fun readTagScalarInt(bytes: ByteArray, byteOrder: ByteOrder, entryOffset: Int, type: Int): Int =
                readTagScalar(bytes, byteOrder, entryOffset, type).toInt()

            private fun readTagScalar(bytes: ByteArray, byteOrder: ByteOrder, entryOffset: Int, type: Int): Long {
                val valueOffset = entryOffset + 8
                return when (type) {
                    TYPE_SHORT -> readShort(bytes, valueOffset, byteOrder).toLong() and 0xFFFF
                    TYPE_LONG -> readInt(bytes, byteOrder, valueOffset).toLong() and 0xFFFF_FFFFL
                    else -> throw IOException("Unsupported TIFF scalar type: $type")
                }
            }

            /**
             * GDAL_NODATA is an ASCII tag (e.g. `"-32767"`), not a numeric one. Returns null when the
             * tag is absent or unparseable so callers keep the raster values untouched.
             */
            private fun readTagAsciiFloat(
                bytes: ByteArray,
                byteOrder: ByteOrder,
                entryOffset: Int,
                type: Int,
                count: Int
            ): Float? {
                if (type != TYPE_ASCII || count <= 0) return null
                val valueField = entryOffset + 8
                val dataOffset = if (count <= 4) valueField else readInt(bytes, byteOrder, valueField)
                val inBounds = dataOffset >= 0 && dataOffset + count <= bytes.size
                return if (!inBounds) {
                    null
                } else {
                    String(bytes, dataOffset, count, Charsets.US_ASCII)
                        .trim { it <= ' ' || it == '\u0000' }
                        .toFloatOrNull()
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
        private const val PREDICTOR_NONE = 1
        private const val PREDICTOR_FLOATING_POINT = 3

        /** Match Explore [DemTileReader] dimension cap. */
        private const val MAX_RASTER_DIMENSION = 10_000

        /** Align with DEM download cap in DefaultDemTileFetcher. */
        private const val MAX_INPUT_BYTES: Long = 80L * 1024 * 1024

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
        private const val TAG_PREDICTOR = 317
        private const val TAG_SAMPLE_FORMAT = 339
        private const val TAG_GDAL_NODATA = 42113

        private const val TYPE_ASCII = 2
        private const val TYPE_SHORT = 3
        private const val TYPE_LONG = 4
    }
}
