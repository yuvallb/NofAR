package com.nofar.core.data.dem

import com.google.common.truth.Truth.assertThat
import java.io.RandomAccessFile
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DemTileBinaryTest {
    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun fullTileGrid_roundTripCornerSamples() {
        val width = 3600
        val height = 3600
        // Wrapped so every sample stays inside the reader's plausible-elevation band.
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
            file.write("NOFAR_DEM".toByteArray(Charsets.US_ASCII))
        }

        assertThat(DemTileReader.hasCurrentFormat(currentFile)).isTrue()
        assertThat(DemTileReader.hasCurrentFormat(legacyFile)).isFalse()
    }

    // Device regression: tiles converted before GDAL_NODATA was honoured still hold Copernicus'
    // -32767 sentinel while the header declares -9999. Accepting it as a real elevation put the Explore
    // skyline eye ~32 km underground, so every azimuth read ~90° and the horizon tracked camera pitch.
    @Test
    fun unnormalizedNoDataSentinels_areRejectedAsElevations() {
        val width = 10
        val height = 10
        val sentinel = -32767f
        val elevations = FloatArray(width * height) { sentinel }
        // One genuine sample in the north-west corner pixel proves the guard is value-based, not blanket.
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
            val expectedRow = ((33.0 - lat) / 1.0 * (height - 1)).toInt().coerceIn(0, height - 1)
            val expectedCol = ((lon - 35.0) / 1.0 * (width - 1)).toInt().coerceIn(0, width - 1)
            val expected = elevations[expectedRow * width + expectedCol]
            assertThat(reader.elevationAt(lat, lon)).isWithin(0.001f).of(expected)
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
