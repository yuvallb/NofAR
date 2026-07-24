package com.nofar.core.visibility

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.AppConfig
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HorizonProfileComputerTest {
    @get:Rule
    val tempDir = TemporaryFolder()

    private val computer = HorizonProfileComputer()
    private val readers = mutableListOf<Map<String, com.nofar.core.data.dem.DemTileReader>>()

    @After
    fun tearDown() {
        readers.forEach(::closeReaders)
    }

    @Test
    fun flatTerrain_allBucketsReturnZeroElevationAngle() {
        val tileLat = 32
        val tileLon = 35
        val reader = VisibilityTestDem.writeFlatTile(tempDir, tileLat, tileLon, elevationM = 200f)
        trackReaders(singleTileReaders(reader, tileLat, tileLon))
        val sampler = singleTileReaders(reader, tileLat, tileLon).toSampler()

        val observerLat = tileLat + 0.5
        val observerLon = tileLon + 0.5
        val observerEyeM = 200.0 + AppConfig.EYE_HEIGHT_METERS

        val profile =
            computer.sweep(
                observerLat = observerLat,
                observerLon = observerLon,
                observerEyeM = observerEyeM,
                sampler = sampler
            )

        assertThat(profile.azimuthStepDeg).isWithin(0.001f).of(AppConfig.HORIZON_AZIMUTH_STEP_DEG)
        assertThat(profile.elevationAnglesDeg.size).isEqualTo((360f / AppConfig.HORIZON_AZIMUTH_STEP_DEG).toInt())
        profile.elevationAnglesDeg.forEach { angle ->
            assertThat(angle).isLessThan(1f)
        }
    }

    @Test
    fun ridgeAlongNorth_producesHigherAngleInNorthBucket() {
        val tileLat = 32
        val tileLon = 35
        val observerLat = tileLat + 0.5
        val observerLon = tileLon + 0.5
        val reader =
            VisibilityTestDem.writeHillTile(
                folder = tempDir,
                tileLat = tileLat,
                tileLon = tileLon,
                baseElevationM = 100f,
                hillElevationM = 800f,
                hillCenterLat = tileLat + 0.55,
                hillCenterLon = tileLon + 0.5,
                hillRadiusM = 400.0
            )
        trackReaders(singleTileReaders(reader, tileLat, tileLon))
        val sampler = singleTileReaders(reader, tileLat, tileLon).toSampler()
        val observerEyeM = 100.0 + AppConfig.EYE_HEIGHT_METERS

        val profile =
            computer.sweep(
                observerLat = observerLat,
                observerLon = observerLon,
                observerEyeM = observerEyeM,
                sampler = sampler
            )

        val northBucket = azimuthBucketIndex(profile, bearingDeg = 0.0)
        val southBucket = azimuthBucketIndex(profile, bearingDeg = 180.0)
        assertThat(profile.elevationAnglesDeg[northBucket]).isGreaterThan(profile.elevationAnglesDeg[southBucket])
    }

    @Test
    fun missingDemData_fallsBackToFlatHorizon() {
        val sampler = DemElevationSampler(emptyMap())
        val profile =
            computer.sweep(
                observerLat = 32.5,
                observerLon = 35.5,
                observerEyeM = 100.0,
                sampler = sampler
            )

        profile.elevationAnglesDeg.forEach { angle ->
            assertThat(angle).isWithin(0.001f).of(0f)
        }
    }

    private fun azimuthBucketIndex(profile: HorizonProfile, bearingDeg: Double): Int {
        var normalized = bearingDeg % 360.0
        if (normalized < 0.0) normalized += 360.0
        return (normalized / profile.azimuthStepDeg).toInt().coerceIn(0, profile.elevationAnglesDeg.lastIndex)
    }

    private fun trackReaders(map: Map<String, com.nofar.core.data.dem.DemTileReader>) {
        readers += map
    }
}
