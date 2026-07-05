package com.nofar.core.visibility

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GoldenProfileTest {
    private val rayMarcher = TerrainRayMarcher()

    @Test
    fun goldenResourceFiles_exist() {
        listOf(
            "golden_profile_flat.json",
            "golden_profile_hill.json",
            "golden_profile_rise.json"
        ).forEach { resource ->
            assertThat(javaClass.classLoader?.getResource(resource)).isNotNull()
        }
    }

    @Test
    fun kotlinRaycast_matchesGoldenFixtures() {
        goldenFixtures().forEach { fixture ->
            val horizonIndex =
                rayMarcher.findHorizonIndex(
                    elevations = fixture.elevations,
                    fixedDistanceM = fixture.fixedDistanceM,
                    observerHeightM = fixture.observerHeightM
                )
            assertThat(horizonIndex).isEqualTo(fixture.expectedHorizonIndex)

            val visible =
                isProfileVisible(
                    elevations = fixture.elevations,
                    observerEyeM = fixture.observerEyeM,
                    targetElevationM = fixture.targetElevationM,
                    totalDistanceM = fixture.totalDistanceM
                )
            assertThat(visible).isEqualTo(fixture.expectedVisible)
        }
    }

    private fun isProfileVisible(
        elevations: List<Double?>,
        observerEyeM: Double,
        targetElevationM: Double,
        totalDistanceM: Double
    ): Boolean {
        val sampleCount = elevations.size
        if (sampleCount < 2) return true
        val stepM = totalDistanceM / (sampleCount - 1)
        return (1 until sampleCount - 1).none { index ->
            val terrain = elevations[index] ?: return@none false
            val distance = index * stepM
            val sightLine = observerEyeM + (targetElevationM - observerEyeM) * (distance / totalDistanceM)
            val bulge = GeoMath.earthBulgeM(distance, totalDistanceM)
            terrain + bulge > sightLine + 0.5
        }
    }

    private fun goldenFixtures(): List<GoldenFixture> = listOf(
        GoldenFixture(
            elevations = listOf(100.0, 100.0, 100.0, 100.0, 100.0),
            fixedDistanceM = 100.0,
            observerHeightM = 1.7,
            expectedHorizonIndex = 4,
            expectedVisible = true,
            observerEyeM = 101.7,
            targetElevationM = 150.0,
            totalDistanceM = 400.0
        ),
        GoldenFixture(
            elevations = listOf(100.0, 100.0, 250.0, 100.0, 120.0),
            fixedDistanceM = 100.0,
            observerHeightM = 1.7,
            expectedHorizonIndex = 2,
            expectedVisible = false,
            observerEyeM = 101.7,
            targetElevationM = 120.0,
            totalDistanceM = 400.0
        ),
        GoldenFixture(
            elevations = listOf(50.0, 60.0, 80.0, 100.0, 130.0),
            fixedDistanceM = 100.0,
            observerHeightM = 1.7,
            expectedHorizonIndex = 4,
            expectedVisible = true,
            observerEyeM = 51.7,
            targetElevationM = 130.0,
            totalDistanceM = 400.0
        )
    )

    private data class GoldenFixture(
        val elevations: List<Double?>,
        val fixedDistanceM: Double,
        val observerHeightM: Double,
        val expectedHorizonIndex: Int,
        val expectedVisible: Boolean,
        val observerEyeM: Double,
        val targetElevationM: Double,
        val totalDistanceM: Double
    )
}
