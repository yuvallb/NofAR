package com.nofar.core.data.dem

import com.google.common.truth.Truth.assertThat
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
        val elevations = FloatArray(width * height) { index -> index.toFloat() }
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
    fun largeGrid_roundTripSample() {
        val width = 100
        val height = 100
        val elevations = buildElevationGrid(width, height) { row, col -> (row * width + col).toFloat() }
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
