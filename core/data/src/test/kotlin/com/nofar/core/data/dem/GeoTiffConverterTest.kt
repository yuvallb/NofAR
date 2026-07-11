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
    fun convert_realCopernicusTile_whenFixturePresent() {
        val fixture = File(System.getProperty("copernicusDemFixture", "/tmp/copernicus_n32_e35.tif"))
        org.junit.Assume.assumeTrue("Copernicus fixture missing: ${fixture.absolutePath}", fixture.exists())

        val output = tempDir.newFile("copernicus.bin")
        val result = DefaultGeoTiffConverter().convert(fixture, tileLat = 32, tileLon = 35, output)

        assertThat(result.width).isEqualTo(3600)
        assertThat(result.height).isEqualTo(3600)
        DemTileReader.open(output).use { reader ->
            assertThat(reader.elevationAt(32.5, 35.5)).isNotNull()
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

    private fun buildDeflateTiledTiff(
        width: Int,
        height: Int,
        tileWidth: Int,
        tileLength: Int,
        samples: FloatArray
    ): ByteArray {
        val tilesAcross = (width + tileWidth - 1) / tileWidth
        val tilesDown = (height + tileLength - 1) / tileLength
        val tileCount = tilesAcross * tilesDown
        val compressedTiles = Array(tileCount) { index ->
            val tileX = index % tilesAcross
            val tileY = index / tilesAcross
            val startX = tileX * tileWidth
            val startY = tileY * tileLength
            val tilePixelWidth = minOf(tileWidth, width - startX)
            val tilePixelHeight = minOf(tileLength, height - startY)
            val tileSamples = FloatArray(tilePixelWidth * tilePixelHeight)
            for (row in 0 until tilePixelHeight) {
                for (col in 0 until tilePixelWidth) {
                    tileSamples[row * tileWidth + col] = samples[(startY + row) * width + (startX + col)]
                }
            }
            val raw =
                ByteBuffer.allocate(tileSamples.size * Float.SIZE_BYTES)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .apply { tileSamples.forEach { putFloat(it) } }
                    .array()
            deflate(raw)
        }

        val ifdOffset = 8
        val offsetsArrayOffset = ifdOffset + 2 + 10 * 12 + 4
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
        buffer.putShort(10)
        writeIfdEntry(buffer, 256, 3, 1, width)
        writeIfdEntry(buffer, 257, 3, 1, height)
        writeIfdEntry(buffer, 258, 3, 1, 32)
        writeIfdEntry(buffer, 259, 3, 1, 8)
        writeIfdEntry(buffer, 277, 3, 1, 1)
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
