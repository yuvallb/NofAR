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

    // H-P1-18: the full-360° skyline sweep (~180 rays × ~101 samples at defaults) must fit inside the
    // remaining §8 visibility budget. Device target is <200 ms as part of the pass; CI JVM is faster
    // but shared runners are noisy, so allow generous headroom and check p95 over repeated sweeps.
    @Test
    fun horizonSweep_flatSampler_completesWithinBudget() {
        val computer = HorizonProfileComputer()
        val sampler = DemSampler { _, _ -> 100f }
        val observerEyeM = 100.0 + AppConfig.EYE_HEIGHT_METERS

        repeat(3) { computer.sweep(32.5, 35.5, observerEyeM, sampler) }
        val samplesMs =
            LongArray(10) {
                val start = System.nanoTime()
                computer.sweep(32.5, 35.5, observerEyeM, sampler)
                (System.nanoTime() - start) / 1_000_000L
            }

        val p95Ms = samplesMs.sorted()[(0.95 * (samplesMs.size - 1)).toInt()]
        assertThat(p95Ms).isLessThan(500L)
    }
}
