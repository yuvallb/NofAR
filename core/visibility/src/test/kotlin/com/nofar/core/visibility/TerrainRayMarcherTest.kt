package com.nofar.core.visibility

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.AppConfig
import com.nofar.core.model.ElevationSource
import com.nofar.core.model.GeoEntity
import com.nofar.core.model.GeoEntityType
import com.nofar.core.model.OsmType
import com.nofar.core.model.ResolutionLevel
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TerrainRayMarcherTest {
    @get:Rule
    val tempDir = TemporaryFolder()

    private val rayMarcher = TerrainRayMarcher()
    private val readers = mutableListOf<Map<String, com.nofar.core.data.dem.DemTileReader>>()

    @Before
    fun setUp() {
        TerrainRayMarcher.applyEarthCurvature = true
    }

    @After
    fun tearDown() {
        readers.forEach(::closeReaders)
        TerrainRayMarcher.applyEarthCurvature = true
    }

    @Test
    fun flatTerrain_allEntitiesInFrontHemisphereVisible() {
        val tileLat = 32
        val tileLon = 35
        val reader = VisibilityTestDem.writeFlatTile(tempDir, tileLat, tileLon, elevationM = 100f)
        trackReaders(singleTileReaders(reader, tileLat, tileLon))
        val sampler = reader.let { singleTileReaders(it, tileLat, tileLon).toSampler() }

        val observerLat = tileLat + 0.5
        val observerLon = tileLon + 0.5
        val targets =
            listOf(
                tileLat + 0.6 to tileLon + 0.6,
                tileLat + 0.4 to tileLon + 0.7,
                tileLat + 0.7 to tileLon + 0.4
            )

        targets.forEach { (targetLat, targetLon) ->
            val distanceM =
                com.nofar.core.model.RegionBounds.haversineDistanceM(
                    observerLat,
                    observerLon,
                    targetLat,
                    targetLon
                )
            val visible =
                rayMarcher.isTargetVisible(
                    observerLat = observerLat,
                    observerLon = observerLon,
                    targetLat = targetLat,
                    targetLon = targetLon,
                    totalDistanceM = distanceM,
                    observerEyeM = 101.7,
                    targetElevationM = 150.0,
                    rayStepM = AppConfig.VISIBILITY_RAY_STEP_METERS,
                    sampler = sampler
                )
            assertThat(visible).isTrue()
        }
    }

    @Test
    fun hillBetweenObserverAndPeak_peakIsOccluded() {
        val tileLat = 32
        val tileLon = 35
        val observerLat = tileLat + 0.5
        val observerLon = tileLon + 0.5
        val peakLat = tileLat + 0.55
        val peakLon = tileLon + 0.55
        val hillLat = tileLat + 0.52
        val hillLon = tileLon + 0.52

        val reader =
            VisibilityTestDem.writeHillTile(
                folder = tempDir,
                tileLat = tileLat,
                tileLon = tileLon,
                baseElevationM = 100f,
                hillElevationM = 300f,
                hillCenterLat = hillLat,
                hillCenterLon = hillLon,
                hillRadiusM = 400.0
            )
        trackReaders(singleTileReaders(reader, tileLat, tileLon))
        val sampler = singleTileReaders(reader, tileLat, tileLon).toSampler()
        val distanceM =
            com.nofar.core.model.RegionBounds.haversineDistanceM(
                observerLat,
                observerLon,
                peakLat,
                peakLon
            )

        val visible =
            rayMarcher.isTargetVisible(
                observerLat = observerLat,
                observerLon = observerLon,
                targetLat = peakLat,
                targetLon = peakLon,
                totalDistanceM = distanceM,
                observerEyeM = 101.7,
                targetElevationM = 350.0,
                rayStepM = 50.0,
                sampler = sampler
            )

        assertThat(visible).isFalse()
    }

    @Test
    fun earthCurvature_hidesDistantPeakThatFlatEarthWouldShow() {
        val tileLat = 47
        val tileLon = 11
        val reader =
            VisibilityTestDem.writeFlatTile(
                tempDir,
                tileLat,
                tileLon,
                elevationM = 0f,
                width = 200,
                height = 200
            )
        trackReaders(singleTileReaders(reader, tileLat, tileLon))
        val sampler = singleTileReaders(reader, tileLat, tileLon).toSampler()

        val observerLat = tileLat + 0.5
        val observerLon = tileLon + 0.5
        val peakLat = tileLat + 0.72
        val peakLon = tileLon + 0.5
        val distanceM =
            com.nofar.core.model.RegionBounds.haversineDistanceM(
                observerLat,
                observerLon,
                peakLat,
                peakLon
            )
        require(distanceM > 20_000.0) { "Test fixture must exceed 20 km" }

        TerrainRayMarcher.applyEarthCurvature = false
        val visibleFlatEarth =
            rayMarcher.isTargetVisible(
                observerLat = observerLat,
                observerLon = observerLon,
                targetLat = peakLat,
                targetLon = peakLon,
                totalDistanceM = distanceM,
                observerEyeM = AppConfig.EYE_HEIGHT_METERS,
                targetElevationM = 10.0,
                rayStepM = AppConfig.VISIBILITY_RAY_STEP_METERS,
                sampler = sampler
            )

        TerrainRayMarcher.applyEarthCurvature = true
        val visibleWithCurvature =
            rayMarcher.isTargetVisible(
                observerLat = observerLat,
                observerLon = observerLon,
                targetLat = peakLat,
                targetLon = peakLon,
                totalDistanceM = distanceM,
                observerEyeM = AppConfig.EYE_HEIGHT_METERS,
                targetElevationM = 10.0,
                rayStepM = AppConfig.VISIBILITY_RAY_STEP_METERS,
                sampler = sampler
            )

        assertThat(visibleFlatEarth).isTrue()
        assertThat(visibleWithCurvature).isFalse()
    }

    @Test
    fun findHorizonIndex_matchesSlopeComparison() {
        val elevations = listOf(100.0, 110.0, 130.0, 120.0, 115.0)
        val index = rayMarcher.findHorizonIndex(elevations, fixedDistanceM = 100.0, observerHeightM = 1.7)
        assertThat(index).isEqualTo(2)
    }

    private fun trackReaders(map: Map<String, com.nofar.core.data.dem.DemTileReader>) {
        readers += map
    }
}

class NoOpVisibilityEngineTest {
    @Test
    fun fakeEngine_returnsAllCandidates() = runTest {
        val engine = NoOpVisibilityEngine()
        val entity = sampleEntity("peak-1", GeoEntityType.PEAK)
        val request =
            VisibilityRequest(
                observerLat = 32.0,
                observerLon = 35.0,
                observerElevationM = 100.0,
                eyeHeightM = AppConfig.EYE_HEIGHT_METERS,
                regionId = java.util.UUID.randomUUID(),
                radiusM = 20_000.0,
                resolutionLevel = ResolutionLevel.Medium,
                demReaders = emptyMap(),
                candidates =
                listOf(
                    VisibilityCandidate(entity = entity, bearingDeg = 45.0, distanceM = 5_000.0)
                ),
                rayStepM = AppConfig.VISIBILITY_RAY_STEP_METERS
            )

        val result = engine.computeVisibleEntities(request)

        assertThat(result.entities).hasSize(1)
        assertThat(result.entities.first().entity.id).isEqualTo("peak-1")
    }
}

class ObserverElevationResolverTest {
    private val resolver = ObserverElevationResolver()

    @Test
    fun gpsAltitudeUsedWhenAccuracyAcceptable() {
        val result =
            resolver.resolve(
                location =
                com.nofar.core.model.UserLocation(
                    latitude = 32.0,
                    longitude = 35.0,
                    altitudeMeters = 450.0,
                    accuracyMeters = 10f,
                    timestampMillis = 0L
                ),
                demElevationM = 100f
            )
        assertThat(result.elevationM).isWithin(0.001).of(450.0)
        assertThat(result.warning).isNull()
    }

    @Test
    fun demFallbackWhenGpsAltitudeMissing() {
        val result =
            resolver.resolve(
                location =
                com.nofar.core.model.UserLocation(
                    latitude = 32.0,
                    longitude = 35.0,
                    altitudeMeters = null,
                    accuracyMeters = 10f,
                    timestampMillis = 0L
                ),
                demElevationM = 220f
            )
        assertThat(result.elevationM).isWithin(0.001).of(220.0)
        assertThat(result.warning).isEqualTo(VisibilityWarning.OBSERVER_ELEVATION_FROM_DEM)
    }

    @Test
    fun seaLevelFallbackWhenNoGpsOrDem() {
        val result =
            resolver.resolve(
                location =
                com.nofar.core.model.UserLocation(
                    latitude = 32.0,
                    longitude = 35.0,
                    altitudeMeters = null,
                    accuracyMeters = 10f,
                    timestampMillis = 0L
                ),
                demElevationM = null
            )
        assertThat(result.elevationM).isEqualTo(0.0)
        assertThat(result.warning).isEqualTo(VisibilityWarning.OBSERVER_ELEVATION_FALLBACK_SEA_LEVEL)
    }
}

class ResolutionLevelFilterTest {
    @Test
    fun basicResolution_excludesPeaksAndNonCityPlaces() {
        assertThat(GeoEntityType.PEAK.matchesResolution(ResolutionLevel.Basic)).isFalse()
        assertThat(GeoEntityType.TOWN.matchesResolution(ResolutionLevel.Basic)).isFalse()
        assertThat(GeoEntityType.CITY.matchesResolution(ResolutionLevel.Basic)).isTrue()
    }
}

private fun sampleEntity(id: String, type: GeoEntityType): GeoEntity = GeoEntity(
    id = id,
    osmType = OsmType.NODE,
    name = id,
    type = type,
    lat = 32.1,
    lon = 35.1,
    elevation = 500.0,
    elevationSource = ElevationSource.OSM_TAG,
    lastSeenAt = Instant.EPOCH
)
