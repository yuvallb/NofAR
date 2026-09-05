package com.nofar.core.data.dem

import com.google.common.truth.Truth.assertThat
import java.io.RandomAccessFile
import kotlin.math.floor
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DemTileBinaryTest {
    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun fullTileGrid_roundTripCornerSamples() {
        val width = 1_200
        val height = 1_200
        val elevations = FloatArray(width * height) { index -> (index % 3_000).toFloat() }
        val file = tempDir.newFile("full-tile.bin")
        DemTileWriter(tileLat = 32, tileLon = 35).write(file, width, height, elevations)

        DemTileReader.open(file).use { reader ->
            assertThat(reader.elevationAt(32.5, 35.5)).isNotNull()
            assertThat(reader.elevationAt(32.01, 35.99)).isNotNull()
            assertThat(reader.elevationAt(32.99, 35.01)).isNotNull()
        }
    }

    @Test
    fun writeAndRead_elevationAtKnownPoints() {
        val width = 10
        val height = 10
        val tileLat = 32
        val tileLon = 35
        val elevations = FloatArray(width * height) { index -> index.toFloat() }
        val file = tempDir.newFile("tile.bin")

        DemTileWriter(tileLat = tileLat, tileLon = tileLon).write(
            outputFile = file,
            width = width,
            height = height,
            elevations = elevations
        )

        DemTileReader.open(file).use { reader ->
            assertThat(reader.width).isEqualTo(width)
            assertThat(reader.height).isEqualTo(height)
            val centerLat = tileLat + 0.55
            val centerLon = tileLon + 0.55
            val value = reader.elevationAt(centerLat, centerLon)
            assertThat(value).isNotNull()
        }
    }

    @Test
    fun currentFormatDetection_rejectsLegacyConverterOutput() {
        val elevations = FloatArray(16) { 100f }
        val currentFile = tempDir.newFile("current.bin")
        DemTileWriter(tileLat = 32, tileLon = 35).write(currentFile, 4, 4, elevations)
        val legacyFile = tempDir.newFile("legacy.bin")
        currentFile.copyTo(legacyFile, overwrite = true)
        RandomAccessFile(legacyFile, "rw").use { file ->
            file.write("NOFAR_DM3".toByteArray(Charsets.US_ASCII))
        }

        assertThat(DemTileReader.hasCurrentFormat(currentFile)).isTrue()
        assertThat(DemTileReader.hasCurrentFormat(legacyFile)).isFalse()
    }

    @Test
    fun unnormalizedNoDataSentinels_areRejectedAsElevations() {
        val width = 10
        val height = 10
        val elevations = FloatArray(width * height) { DemBinaryFormat.HEADER_NO_DATA_VALUE }
        elevations[0] = 72f
        val file = tempDir.newFile("sentinel.bin")

        DemTileWriter(tileLat = 32, tileLon = 35).write(file, width, height, elevations)

        DemTileReader.open(file).use { reader ->
            assertThat(reader.elevationAt(32.5, 35.5)).isNull()
            assertThat(reader.elevationAt(32.999, 35.0)).isWithin(0.001f).of(72f)
        }
    }

    @Test
    fun implausiblyHighSentinel_isRejected() {
        val width = 4
        val height = 4
        val elevations = FloatArray(width * height) { 32767f }
        val file = tempDir.newFile("high-sentinel.bin")

        DemTileWriter(tileLat = 32, tileLon = 35).write(file, width, height, elevations)

        DemTileReader.open(file).use { reader ->
            assertThat(reader.elevationAt(32.5, 35.5)).isNull()
        }
    }

    @Test
    fun largeGrid_roundTripSample() {
        val width = 100
        val height = 100
        val elevations = buildElevationGrid(width, height) { row, col -> ((row * width + col) % 3_000).toFloat() }
        val file = tempDir.newFile("large.bin")
        DemTileWriter(tileLat = 32, tileLon = 35).write(file, width, height, elevations)

        DemTileReader.open(file).use { reader ->
            val lat = 32.5
            val lon = 35.5
            val fracX = (lon - 35.0) / 1.0
            val fracY = (33.0 - lat) / 1.0
            val expectedCol = floor(fracX * width).toInt().coerceIn(0, width - 1)
            val expectedRow = floor(fracY * height).toInt().coerceIn(0, height - 1)
            val expected = elevations[expectedRow * width + expectedCol]
            assertThat(reader.elevationAt(lat, lon)).isWithin(0.001f).of(expected)
        }
    }

    @Test
    fun int16Quantization_roundsToNearestMeter() {
        val elevations = floatArrayOf(100.4f, 100.6f, -500.5f)
        val file = tempDir.newFile("quantize.bin")
        DemTileWriter(tileLat = 32, tileLon = 35).write(file, 3, 1, elevations)

        DemTileReader.open(file).use { reader ->
            assertThat(reader.elevationAt(32.0, 35.0)).isWithin(0.001f).of(100f)
            assertThat(reader.elevationAt(32.0, 35.4)).isWithin(0.001f).of(101f)
            assertThat(reader.elevationAt(32.0, 35.7)).isWithin(0.001f).of(-500f)
        }
    }

    @Test
    fun polarWidth_nonSquareDimensions() {
        val width = 800
        val height = 1_200
        val elevations = FloatArray(width * height) { 250f }
        val file = tempDir.newFile("polar.bin")
        DemTileWriter(tileLat = 55, tileLon = 10).write(file, width, height, elevations)

        DemTileReader.open(file).use { reader ->
            assertThat(reader.width).isEqualTo(width)
            assertThat(reader.height).isEqualTo(height)
            assertThat(reader.elevationAt(55.5, 10.5)).isWithin(0.001f).of(250f)
        }
    }
}

private fun buildElevationGrid(width: Int, height: Int, init: (row: Int, col: Int) -> Float): FloatArray {
    val array = FloatArray(width * height)
    for (row in 0 until height) {
        for (col in 0 until width) {
            array[row * width + col] = init(row, col)
        }
    }
    return array
}
