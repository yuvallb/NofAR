package com.nofar.core.data.dem

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Deflater
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GeoTiffConverterTest {
    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun convert_uncompressedStripFloat32_producesReadableDemTile() {
        val width = 4
        val height = 4
        val samples =
            floatArrayOf(
                100f, 101f, 102f, 103f,
                110f, 111f, 112f, 113f,
                120f, 121f, 122f, 123f,
                130f, 131f, 132f, 133f
            )
        val input = tempDir.newFile("strip.tif")
        input.writeBytes(buildUncompressedStripTiff(width, height, samples))

        val output = tempDir.newFile("strip.bin")
        val result = DefaultGeoTiffConverter().convert(input, tileLat = 32, tileLon = 35, output)

        assertThat(result.width).isEqualTo(width)
        assertThat(result.height).isEqualTo(height)
        DemTileReader.open(output).use { reader ->
            assertThat(reader.elevationAt(32.5, 35.5)).isWithin(0.001f).of(111f)
        }
    }

    @Test
    fun convert_deflateTiledFloat32_producesReadableDemTile() {
        val width = 8
        val height = 8
        val tileWidth = 4
        val samples = FloatArray(width * height) { index -> 200f + index }
        val input = tempDir.newFile("tiled.tif")
        input.writeBytes(buildDeflateTiledTiff(width, height, tileWidth, tileWidth, samples))

        val output = tempDir.newFile("tiled.bin")
        val result = DefaultGeoTiffConverter().convert(input, tileLat = 32, tileLon = 35, output)

        assertThat(result.width).isEqualTo(width)
        assertThat(result.height).isEqualTo(height)
        DemTileReader.open(output).use { reader ->
            assertThat(reader.elevationAt(32.25, 35.25)).isNotNull()
            assertThat(reader.elevationAt(32.75, 35.75)).isNotNull()
        }
    }

    @Test
    fun convert_deflateTiledFloat32_preservesPaddedEdgeTileStride() {
        val width = 10
        val height = 6
        val tileWidth = 4
        val tileLength = 4
        val samples = FloatArray(width * height) { index -> 300f + index }
        val input = tempDir.newFile("padded-edge-tile.tif")
        input.writeBytes(buildDeflateTiledTiff(width, height, tileWidth, tileLength, samples))

        val output = tempDir.newFile("padded-edge-tile.bin")
        DefaultGeoTiffConverter().convert(input, tileLat = 32, tileLon = 35, output)

        assertThat(readBinElevations(output, width, height).asList())
            .containsExactlyElementsIn(samples.asList())
            .inOrder()
    }

    @Test
    fun convert_deflateTiledFloat32_reversesFloatingPointPredictor() {
        val width = 10
        val height = 6
        val tileWidth = 4
        val tileLength = 4
        val samples = FloatArray(width * height) { index -> -430f + index * 3.25f }
        val input = tempDir.newFile("floating-point-predictor.tif")
        input.writeBytes(
            buildDeflateTiledTiff(
                width = width,
                height = height,
                tileWidth = tileWidth,
                tileLength = tileLength,
                samples = samples,
                predictor = 3
            )
        )

        val output = tempDir.newFile("floating-point-predictor.bin")
        DefaultGeoTiffConverter().convert(input, tileLat = 32, tileLon = 35, output)

        assertThat(readBinElevations(output, width, height).asList())
            .containsExactlyElementsIn(samples.asList())
            .inOrder()
    }

    // Copernicus GLO-30 declares its no-data as the ASCII GDAL_NODATA tag (-32767). The converter used to
    // ignore the tag and stamp the header with -9999, so the sentinel reached Explore as a real elevation.
    @Test
    fun convert_honoursGdalNoDataTag_normalizingSentinelToHeaderValue() {
        val width = 4
        val height = 4
        val sentinel = -32767f
        val samples = FloatArray(width * height) { sentinel }
        samples[0] = 72f
        val input = tempDir.newFile("nodata.tif")
        input.writeBytes(buildStripTiffWithNoData(width, height, samples, noDataText = "-32767"))

        val output = tempDir.newFile("nodata.bin")
        val result = DefaultGeoTiffConverter().convert(input, tileLat = 32, tileLon = 35, output)

        assertThat(result.noDataValue).isWithin(0.001f).of(DemBinaryFormat.DEFAULT_NO_DATA_VALUE)
        DemTileReader.open(output).use { reader ->
            assertThat(reader.elevationAt(32.5, 35.5)).isNull()
            assertThat(reader.elevationAt(32.999, 35.0)).isWithin(0.001f).of(72f)
        }
    }

    @Test
    fun convert_realCopernicusTile_whenFixturePresent() {
        val fixturePath = System.getProperty("copernicusDemFixture") ?: "/tmp/copernicus_n32_e35.tif"
        val fixture = File(fixturePath)
        org.junit.Assume.assumeTrue("Copernicus fixture missing: ${fixture.absolutePath}", fixture.exists())

        val output = tempDir.newFile("copernicus.bin")
        val result = DefaultGeoTiffConverter().convert(fixture, tileLat = 32, tileLon = 35, output)

        assertThat(result.width).isEqualTo(3600)
        assertThat(result.height).isEqualTo(3600)
        DemTileReader.open(output).use { reader ->
            var validSamples = 0
            for (row in 1..9) {
                for (column in 1..9) {
                    val lat = 32.0 + row / 10.0
                    val lon = 35.0 + column / 10.0
                    if (reader.elevationAt(lat, lon) != null) validSamples += 1
                }
            }
            assertThat(validSamples).isAtLeast(75)
        }
    }

    private fun buildUncompressedStripTiff(width: Int, height: Int, samples: FloatArray): ByteArray {
        val sampleBytes = ByteBuffer.allocate(samples.size * Float.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply { samples.forEach { putFloat(it) } }
            .array()
        val ifdOffset = 8
        val dataOffset = ifdOffset + 2 + 8 * 12 + 4
        val buffer = ByteBuffer.allocate(dataOffset + sampleBytes.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put('I'.code.toByte())
        buffer.put('I'.code.toByte())
        buffer.putShort(42)
        buffer.putInt(ifdOffset)
        buffer.putShort(8) // entry count
        writeIfdEntry(buffer, 256, 3, 1, width)
        writeIfdEntry(buffer, 257, 3, 1, height)
        writeIfdEntry(buffer, 258, 3, 1, 32)
        writeIfdEntry(buffer, 259, 3, 1, 1)
        writeIfdEntry(buffer, 273, 4, 1, dataOffset)
        writeIfdEntry(buffer, 277, 3, 1, 1)
        writeIfdEntry(buffer, 278, 3, 1, height)
        writeIfdEntry(buffer, 279, 4, 1, sampleBytes.size)
        buffer.putInt(0)
        buffer.put(sampleBytes)
        return buffer.array()
    }

    /** Same layout as [buildUncompressedStripTiff] plus an out-of-line ASCII GDAL_NODATA entry. */
    private fun buildStripTiffWithNoData(width: Int, height: Int, samples: FloatArray, noDataText: String): ByteArray {
        val sampleBytes = ByteBuffer.allocate(samples.size * Float.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply { samples.forEach { putFloat(it) } }
            .array()
        val entryCount = 9
        val ifdOffset = 8
        val asciiBytes = (noDataText + "\u0000").toByteArray(Charsets.US_ASCII)
        val asciiOffset = ifdOffset + 2 + entryCount * 12 + 4
        val dataOffset = asciiOffset + asciiBytes.size
        val buffer = ByteBuffer.allocate(dataOffset + sampleBytes.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put('I'.code.toByte())
        buffer.put('I'.code.toByte())
        buffer.putShort(42)
        buffer.putInt(ifdOffset)
        buffer.putShort(entryCount.toShort())
        writeIfdEntry(buffer, 256, 3, 1, width)
        writeIfdEntry(buffer, 257, 3, 1, height)
        writeIfdEntry(buffer, 258, 3, 1, 32)
        writeIfdEntry(buffer, 259, 3, 1, 1)
        writeIfdEntry(buffer, 273, 4, 1, dataOffset)
        writeIfdEntry(buffer, 277, 3, 1, 1)
        writeIfdEntry(buffer, 278, 3, 1, height)
        writeIfdEntry(buffer, 279, 4, 1, sampleBytes.size)
        writeIfdEntry(buffer, 42113, 2, asciiBytes.size, asciiOffset)
        buffer.putInt(0)
        buffer.put(asciiBytes)
        buffer.put(sampleBytes)
        return buffer.array()
    }

    private fun buildDeflateTiledTiff(
        width: Int,
        height: Int,
        tileWidth: Int,
        tileLength: Int,
        samples: FloatArray,
        predictor: Int = 1
    ): ByteArray {
        val tilesAcross = (width + tileWidth - 1) / tileWidth
        val tilesDown = (height + tileLength - 1) / tileLength
        val tileCount = tilesAcross * tilesDown
        val compressedTiles =
            buildCompressedTiles(width, height, tileWidth, tileLength, samples, predictor)

        val entryCount = if (predictor == 3) 11 else 10
        val ifdOffset = 8
        val offsetsArrayOffset = ifdOffset + 2 + entryCount * 12 + 4
        val countsArrayOffset = offsetsArrayOffset + tileCount * 4
        var dataOffset = countsArrayOffset + tileCount * 4
        val tileOffsets = IntArray(tileCount)
        val tileByteCounts = IntArray(tileCount)
        compressedTiles.forEachIndexed { index, bytes ->
            tileOffsets[index] = dataOffset
            tileByteCounts[index] = bytes.size
            dataOffset += bytes.size
        }

        val buffer = ByteBuffer.allocate(dataOffset).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put('I'.code.toByte())
        buffer.put('I'.code.toByte())
        buffer.putShort(42)
        buffer.putInt(ifdOffset)
        buffer.putShort(entryCount.toShort())
        writeIfdEntry(buffer, 256, 3, 1, width)
        writeIfdEntry(buffer, 257, 3, 1, height)
        writeIfdEntry(buffer, 258, 3, 1, 32)
        writeIfdEntry(buffer, 259, 3, 1, 8)
        writeIfdEntry(buffer, 277, 3, 1, 1)
        if (predictor == 3) writeIfdEntry(buffer, 317, 3, 1, predictor)
        writeIfdEntry(buffer, 322, 3, 1, tileWidth)
        writeIfdEntry(buffer, 323, 3, 1, tileLength)
        writeIfdEntry(buffer, 324, 4, tileCount, offsetsArrayOffset)
        writeIfdEntry(buffer, 325, 4, tileCount, countsArrayOffset)
        writeIfdEntry(buffer, 339, 3, 1, 3)
        buffer.putInt(0)
        tileOffsets.forEach { buffer.putInt(it) }
        tileByteCounts.forEach { buffer.putInt(it) }
        compressedTiles.forEach { buffer.put(it) }
        return buffer.array()
    }

    private fun buildCompressedTiles(
        width: Int,
        height: Int,
        tileWidth: Int,
        tileLength: Int,
        samples: FloatArray,
        predictor: Int
    ): Array<ByteArray> {
        val tilesAcross = (width + tileWidth - 1) / tileWidth
        val tilesDown = (height + tileLength - 1) / tileLength
        return Array(tilesAcross * tilesDown) { index ->
            val tileX = index % tilesAcross
            val tileY = index / tilesAcross
            val startX = tileX * tileWidth
            val startY = tileY * tileLength
            val tilePixelWidth = minOf(tileWidth, width - startX)
            val tilePixelHeight = minOf(tileLength, height - startY)
            // TIFF edge tiles retain the declared full tile dimensions and row stride. Pixels
            // outside the image are padding and must not shift valid rows during conversion.
            val tileSamples = FloatArray(tileWidth * tileLength) { DemBinaryFormat.DEFAULT_NO_DATA_VALUE }
            for (row in 0 until tilePixelHeight) {
                for (col in 0 until tilePixelWidth) {
                    tileSamples[row * tileWidth + col] = samples[(startY + row) * width + (startX + col)]
                }
            }
            val sampleBytes =
                ByteBuffer.allocate(tileSamples.size * Float.SIZE_BYTES)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .apply { tileSamples.forEach { putFloat(it) } }
                    .array()
            val encodedBytes =
                if (predictor == 3) {
                    encodeFloatingPointPredictor(sampleBytes, rowSampleCount = tileWidth)
                } else {
                    sampleBytes
                }
            deflate(encodedBytes)
        }
    }

    private fun encodeFloatingPointPredictor(input: ByteArray, rowSampleCount: Int): ByteArray {
        val bytesPerSample = Float.SIZE_BYTES
        val rowByteCount = rowSampleCount * bytesPerSample
        val output = ByteArray(input.size)
        var rowOffset = 0
        while (rowOffset < input.size) {
            for (sampleIndex in 0 until rowSampleCount) {
                for (byteIndex in 0 until bytesPerSample) {
                    val planeIndex = bytesPerSample - byteIndex - 1
                    output[rowOffset + planeIndex * rowSampleCount + sampleIndex] =
                        input[rowOffset + sampleIndex * bytesPerSample + byteIndex]
                }
            }
            for (index in rowByteCount - 1 downTo 1) {
                val difference = (output[rowOffset + index].toInt() and 0xFF) -
                    (output[rowOffset + index - 1].toInt() and 0xFF)
                output[rowOffset + index] = difference.toByte()
            }
            rowOffset += rowByteCount
        }
        return output
    }

    private fun readBinElevations(file: File, width: Int, height: Int): FloatArray {
        val buffer = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(DemBinaryFormat.HEADER_SIZE_BYTES)
        return FloatArray(width * height) { buffer.float }
    }

    private fun writeIfdEntry(buffer: ByteBuffer, tag: Int, type: Int, count: Int, value: Int) {
        buffer.putShort(tag.toShort())
        buffer.putShort(type.toShort())
        buffer.putInt(count)
        buffer.putInt(value)
    }

    private fun deflate(input: ByteArray): ByteArray {
        val deflater = Deflater()
        return try {
            deflater.setInput(input)
            deflater.finish()
            val output = ByteArrayOutputStream(input.size)
            val scratch = ByteArray(1024)
            while (!deflater.finished()) {
                val count = deflater.deflate(scratch)
                output.write(scratch, 0, count)
            }
            output.toByteArray()
        } finally {
            deflater.end()
        }
    }
}
