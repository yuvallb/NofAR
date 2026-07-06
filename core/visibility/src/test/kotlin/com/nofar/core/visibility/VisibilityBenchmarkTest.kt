package com.nofar.core.visibility

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.AppConfig
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VisibilityBenchmarkTest {
    @get:Rule
    val tempDir = TemporaryFolder()

    @After
    fun tearDown() {
        TerrainRayMarcher.applyEarthCurvature = true
    }

    @Test
    fun hundredRaycasts_onLargeDem_completeWithinBudget() {
        val tileLat = 32
        val tileLon = 35
        val width = 3600
        val height = 3600
        val elevations = FloatArray(width * height) { 100f }
        val file = tempDir.newFile("benchmark.bin")
        com.nofar.core.data.dem.DemTileWriter(tileLat = tileLat, tileLon = tileLon).write(
            file,
            width,
            height,
            elevations
        )
        val reader = com.nofar.core.data.dem.DemTileReader.open(file)
        val demReaders = singleTileReaders(reader, tileLat, tileLon)
        val sampler = demReaders.toSampler()
        val rayMarcher = TerrainRayMarcher()
        val observerLat = tileLat + 0.5
        val observerLon = tileLon + 0.5

        val startNanos = System.nanoTime()
        repeat(100) { index ->
            val bearing = index * 3.6
            val (targetLat, targetLon) = GeoMath.destinationPoint(observerLat, observerLon, bearing, 20_000.0)
            rayMarcher.isTargetVisible(
                observerLat = observerLat,
                observerLon = observerLon,
                targetLat = targetLat,
                targetLon = targetLon,
                totalDistanceM = 20_000.0,
                observerEyeM = 100.0 + AppConfig.EYE_HEIGHT_METERS,
                targetElevationM = 200.0,
                rayStepM = AppConfig.VISIBILITY_RAY_STEP_METERS,
                sampler = sampler
            )
        }
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
        closeReaders(demReaders)

        // CI runs on JVM/emulator — allow relaxed threshold; physical device target is 200 ms.
        assertThat(elapsedMs).isLessThan(2_000L)
    }
}
