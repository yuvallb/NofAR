package com.nofar.core.visibility

import com.google.common.truth.Truth.assertThat
import com.nofar.core.common.DefaultDispatchers
import com.nofar.core.model.AppConfig
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DemRaycastVisibilityEngineTest {
    @get:Rule
    val tempDir = TemporaryFolder()

    @After
    fun tearDown() {
        TerrainRayMarcher.applyEarthCurvature = true
    }

    @Test
    fun unobstructedPeakIncluded_onFlatTerrain() = runTest {
        val tileLat = 32
        val tileLon = 35
        val reader = VisibilityTestDem.writeFlatTile(tempDir, tileLat, tileLon, elevationM = 100f)
        val demReaders = singleTileReaders(reader, tileLat, tileLon)
        val engine = DemRaycastVisibilityEngine(DefaultDispatchers)
        val visiblePeak = sampleEntity("visible", tileLat + 0.55, tileLon + 0.55, 150.0)
        val observerLat = tileLat + 0.5
        val observerLon = tileLon + 0.5

        val result =
            engine.computeVisibleEntities(
                buildRequest(
                    observerLat = observerLat,
                    observerLon = observerLon,
                    demReaders = demReaders,
                    candidates = listOf(toCandidate(visiblePeak, observerLat, observerLon)),
                    rayStepM = 50.0
                )
            )

        assertThat(result.entities.map { it.entity.id }).contains("visible")
        closeReaders(demReaders)
    }

    @Test
    fun occludedPeakExcluded_whenHillBlocksLineOfSight() = runTest {
        val tileLat = 32
        val tileLon = 35
        val hillReaders =
            singleTileReaders(
                VisibilityTestDem.writeHillTile(
                    folder = tempDir,
                    tileLat = tileLat,
                    tileLon = tileLon,
                    baseElevationM = 100f,
                    hillElevationM = 300f,
                    hillCenterLat = tileLat + 0.52,
                    hillCenterLon = tileLon + 0.52,
                    hillRadiusM = 400.0
                ),
                tileLat,
                tileLon
            )
        val engine = DemRaycastVisibilityEngine(DefaultDispatchers)
        val hiddenPeak = sampleEntity("hidden", tileLat + 0.58, tileLon + 0.58, 350.0)
        val observerLat = tileLat + 0.5
        val observerLon = tileLon + 0.5

        val result =
            engine.computeVisibleEntities(
                buildRequest(
                    observerLat = observerLat,
                    observerLon = observerLon,
                    demReaders = hillReaders,
                    candidates = listOf(toCandidate(hiddenPeak, observerLat, observerLon)),
                    rayStepM = 50.0
                )
            )

        assertThat(result.entities.map { it.entity.id }).doesNotContain("hidden")
        closeReaders(hillReaders)
    }

    private fun buildRequest(
        observerLat: Double,
        observerLon: Double,
        demReaders: Map<String, com.nofar.core.data.dem.DemTileReader>,
        candidates: List<VisibilityCandidate>,
        rayStepM: Double
    ): VisibilityRequest = VisibilityRequest(
        observerLat = observerLat,
        observerLon = observerLon,
        observerElevationM = 100.0,
        eyeHeightM = AppConfig.EYE_HEIGHT_METERS,
        regionId = java.util.UUID.randomUUID(),
        radiusM = 20_000.0,
        resolutionLevel = com.nofar.core.model.ResolutionLevel.Medium,
        demReaders = demReaders,
        candidates = candidates,
        rayStepM = rayStepM
    )

    private fun toCandidate(entity: com.nofar.core.model.GeoEntity, observerLat: Double, observerLon: Double) =
        VisibilityCandidate(
            entity = entity,
            bearingDeg = GeoMath.initialBearingDeg(observerLat, observerLon, entity.lat, entity.lon),
            distanceM =
            com.nofar.core.model.RegionBounds.haversineDistanceM(
                observerLat,
                observerLon,
                entity.lat,
                entity.lon
            )
        )
}

private fun sampleEntity(id: String, lat: Double, lon: Double, elevation: Double): com.nofar.core.model.GeoEntity =
    com.nofar.core.model.GeoEntity(
        id = id,
        osmType = com.nofar.core.model.OsmType.NODE,
        name = id,
        type = com.nofar.core.model.GeoEntityType.PEAK,
        lat = lat,
        lon = lon,
        elevation = elevation,
        elevationSource = com.nofar.core.model.ElevationSource.OSM_TAG,
        lastSeenAt = java.time.Instant.EPOCH
    )
