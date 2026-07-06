package com.nofar.core.visibility

import com.nofar.core.data.dem.DemTileReader
import com.nofar.core.data.dem.DemTileWriter
import java.io.File
import org.junit.rules.TemporaryFolder

internal object VisibilityTestDem {
    fun writeFlatTile(
        folder: TemporaryFolder,
        tileLat: Int,
        tileLon: Int,
        elevationM: Float,
        width: Int = 100,
        height: Int = 100
    ): DemTileReader {
        val elevations = FloatArray(width * height) { elevationM }
        val file = folder.newFile("tile_${tileLat}_$tileLon.bin")
        DemTileWriter(tileLat = tileLat, tileLon = tileLon).write(file, width, height, elevations)
        return DemTileReader.open(file)
    }

    fun writeHillTile(
        folder: TemporaryFolder,
        tileLat: Int,
        tileLon: Int,
        baseElevationM: Float,
        hillElevationM: Float,
        hillCenterLat: Double,
        hillCenterLon: Double,
        hillRadiusM: Double = 500.0,
        width: Int = 360,
        height: Int = 360
    ): DemTileReader {
        val elevations = FloatArray(width * height)
        for (row in 0 until height) {
            for (col in 0 until width) {
                val lat = tileLat + 1.0 - (row.toDouble() / (height - 1))
                val lon = tileLon + (col.toDouble() / (width - 1))
                val distanceM =
                    com.nofar.core.model.RegionBounds.haversineDistanceM(
                        hillCenterLat,
                        hillCenterLon,
                        lat,
                        lon
                    )
                val value =
                    if (distanceM <= hillRadiusM) {
                        hillElevationM
                    } else {
                        baseElevationM
                    }
                elevations[row * width + col] = value
            }
        }
        val file = folder.newFile("hill_${tileLat}_$tileLon.bin")
        DemTileWriter(tileLat = tileLat, tileLon = tileLon).write(file, width, height, elevations)
        return DemTileReader.open(file)
    }

    fun tileId(tileLat: Int, tileLon: Int): String = com.nofar.core.model.DemTileId.fromCoordinates(tileLat, tileLon)
}

internal fun Map<String, DemTileReader>.toSampler(): DemElevationSampler = DemElevationSampler(this)

internal fun singleTileReaders(reader: DemTileReader, tileLat: Int, tileLon: Int): Map<String, DemTileReader> =
    mapOf(VisibilityTestDem.tileId(tileLat, tileLon) to reader)

internal fun closeReaders(readers: Map<String, DemTileReader>) {
    readers.values.forEach { runCatching { it.close() } }
}

internal fun File.toReader(): DemTileReader = DemTileReader.open(this)
