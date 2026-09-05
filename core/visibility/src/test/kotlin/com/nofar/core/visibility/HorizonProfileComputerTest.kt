package com.nofar.core.visibility

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.AppConfig
import com.nofar.core.model.GeoMathBounds
import kotlin.math.abs
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

    // H-P0-T04: GPS ~15 m below the local DEM must not produce a steep positive skyline
    // ("line only when pointing at the sky"). With the DEM-aligned eye every bucket stays near flat.
    @Test
    fun gpsBelowLocalDem_doesNotProduceSteepPositiveHorizon() {
        val tileLat = 32
        val tileLon = 35
        val reader = VisibilityTestDem.writeFlatTile(tempDir, tileLat, tileLon, elevationM = 120f)
        trackReaders(singleTileReaders(reader, tileLat, tileLon))
        val sampler = singleTileReaders(reader, tileLat, tileLon).toSampler()

        val observerLat = tileLat + 0.5
        val observerLon = tileLon + 0.5
        // GPS reports 105 m — 15 m below the 120 m DEM ground. Eye resolves DEM-aligned.
        val eye = ObserverEyeAltitude.resolve(observerElevationM = 105.0, demGroundM = 120f)

        val profile =
            computer.sweep(
                observerLat = observerLat,
                observerLon = observerLon,
                observerEyeM = eye.eyeM,
                sampler = sampler
            )

        profile.elevationAnglesDeg.forEach { angle ->
            assertThat(abs(angle)).isLessThan(2f)
        }
    }

    // H-P0-T05: the opposite mismatch — GPS far above the DEM — also stays flat because the eye is
    // resolved from the DEM ground, not the GPS altitude.
    @Test
    fun gpsAboveLocalDem_doesNotProduceSteepNegativeHorizon() {
        val tileLat = 32
        val tileLon = 35
        val reader = VisibilityTestDem.writeFlatTile(tempDir, tileLat, tileLon, elevationM = 80f)
        trackReaders(singleTileReaders(reader, tileLat, tileLon))
        val sampler = singleTileReaders(reader, tileLat, tileLon).toSampler()

        val observerLat = tileLat + 0.5
        val observerLon = tileLon + 0.5
        val eye = ObserverEyeAltitude.resolve(observerElevationM = 120.0, demGroundM = 80f)

        val profile =
            computer.sweep(
                observerLat = observerLat,
                observerLon = observerLon,
                observerEyeM = eye.eyeM,
                sampler = sampler
            )

        profile.elevationAnglesDeg.forEach { angle ->
            assertThat(abs(angle)).isLessThan(2f)
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

    // A circular 3-point moving average used to crush a single-bucket ridge to ~1/3 of its height,
    // which let HorizonProjector draw a continuous stroke through terrain that should leave the
    // vertical frustum (H-DEC-1 Option A). The peak must survive the sweep intact.
    @Test
    fun narrowNorthSpike_isNotAttenuatedByCircularSmoothing() {
        val observerLat = 32.5
        val observerLon = 35.5
        val ridgeDistanceM = 2_000.0
        val sampler =
            DemSampler { lat, lon ->
                val distanceM =
                    GeoMathBounds.haversineDistanceM(observerLat, observerLon, lat, lon)
                if (distanceM < 50.0) {
                    100f
                } else {
                    val bearingDeg = GeoMath.initialBearingDeg(observerLat, observerLon, lat, lon)
                    val nearNorth = bearingDeg <= 0.5 || bearingDeg >= 359.5
                    val onRidge =
                        nearNorth && kotlin.math.abs(distanceM - ridgeDistanceM) < AppConfig.HORIZON_RAY_STEP_M
                    if (onRidge) 800f else 100f
                }
            }

        val profile =
            computer.sweep(
                observerLat = observerLat,
                observerLon = observerLon,
                observerEyeM = 100.0 + AppConfig.EYE_HEIGHT_METERS,
                sampler = sampler,
                maxRadiusM = 5_000.0
            )

        val northBucket = azimuthBucketIndex(profile, bearingDeg = 0.0)
        val neighborBucket = (northBucket + 1) % profile.elevationAnglesDeg.size
        val northAngle = profile.elevationAnglesDeg[northBucket]
        val neighborAngle = profile.elevationAnglesDeg[neighborBucket]
        // Analytic peak ≈ atan((700)/2000) ≈ 19.3°. A 3-point average with flat neighbors would
        // crush it below ~7°. Require the raw peak to survive well above that floor.
        assertThat(northAngle).isGreaterThan(15f)
        assertThat(neighborAngle).isLessThan(2f)
    }

    // H-P1-07: a tile hole (null samples) stops the ray. A far ridge reachable only *through* the hole
    // must not be seen — hold-last would have invented terrain across the gap and drawn a false lobe.
    @Test
    fun missingSamplesBeyondCutoff_breakRayHidesFarRidge() {
        val observerLat = 32.5
        val observerLon = 35.5
        val cutoffM = 3_000.0
        val holeEndM = 6_000.0
        val sampler =
            DemSampler { lat, lon ->
                val distanceM =
                    GeoMathBounds.haversineDistanceM(observerLat, observerLon, lat, lon)
                when {
                    distanceM <= cutoffM + 1.0 -> 100f
                    distanceM <= holeEndM -> null
                    else -> 2_000f
                }
            }

        val profile =
            computer.sweep(
                observerLat = observerLat,
                observerLon = observerLon,
                observerEyeM = 100.0 + AppConfig.EYE_HEIGHT_METERS,
                sampler = sampler,
                maxRadiusM = 15_000.0
            )

        profile.elevationAnglesDeg.forEach { angle ->
            assertThat(angle).isLessThan(1f)
        }
    }

    // H-P1-06: horizon bulge shares TerrainRayMarcher's formula with D = path length. Equal path lengths
    // agree exactly; a longer horizon ray intentionally differs from a nearer entity target.
    @Test
    fun earthBulge_matchesEntitySemantics_atEqualPathLength_andDiffersForLongerRay() {
        val targetDistanceM = 5_000.0
        val midpointM = targetDistanceM / 2.0
        val entityBulge = GeoMath.earthBulgeM(midpointM, targetDistanceM)
        val horizonBulgeSamePath = GeoMath.earthBulgeM(midpointM, targetDistanceM)
        val horizonBulgeLongRay = GeoMath.earthBulgeM(midpointM, 25_000.0)

        assertThat(horizonBulgeSamePath).isWithin(1e-9).of(entityBulge)
        assertThat(horizonBulgeLongRay).isGreaterThan(entityBulge)
    }

    // Uncapping the skyline: a ridge beyond the old 15 km budget must appear once the sweep reaches
    // the full collection radius (25 km for a max-size region).
    @Test
    fun farRidge_beyondOldCap_isAbsentAt15km_andPresentAt25km() {
        val observerLat = 32.5
        val observerLon = 35.5
        val ridgeDistanceM = 18_000.0
        val ridgeElevationM = 1_500f
        val sampler =
            DemSampler { lat, lon ->
                val distanceM =
                    GeoMathBounds.haversineDistanceM(observerLat, observerLon, lat, lon)
                when {
                    distanceM < 50.0 -> 100f
                    kotlin.math.abs(distanceM - ridgeDistanceM) < AppConfig.HORIZON_RAY_STEP_M ->
                        ridgeElevationM
                    else -> 100f
                }
            }
        val observerEyeM = 100.0 + AppConfig.EYE_HEIGHT_METERS

        val cappedProfile =
            computer.sweep(
                observerLat = observerLat,
                observerLon = observerLon,
                observerEyeM = observerEyeM,
                sampler = sampler,
                maxRadiusM = 15_000.0
            )
        val fullProfile =
            computer.sweep(
                observerLat = observerLat,
                observerLon = observerLon,
                observerEyeM = observerEyeM,
                sampler = sampler,
                maxRadiusM = 25_000.0
            )

        val northBucket = azimuthBucketIndex(cappedProfile, bearingDeg = 0.0)
        assertThat(cappedProfile.elevationAnglesDeg[northBucket]).isLessThan(1f)
        assertThat(fullProfile.elevationAnglesDeg[northBucket]).isGreaterThan(2f)
    }

    // Device regression (screenshot: "mean 78.7° pitch 78.4°"). Documents *why* DemTileReader must reject
    // no-data sentinels: if the observer's ground sample is Copernicus' -32767, the eye sits ~32 km
    // underground and every ray's nearest sample reads near-vertical, so the outline follows camera pitch
    // and appears when pointing at the sky. Locks the failure mode this guard exists to prevent.
    @Test
    fun sentinelObserverGround_wouldProduceNearVerticalSkyline() {
        val observerLat = 32.5
        val observerLon = 35.5
        val sentinelGroundM = -32_767.0
        val sampler =
            DemSampler { lat, lon ->
                val distanceM =
                    GeoMathBounds.haversineDistanceM(observerLat, observerLon, lat, lon)
                if (distanceM < 1.0) sentinelGroundM.toFloat() else 72f
            }

        val profile =
            computer.sweep(
                observerLat = observerLat,
                observerLon = observerLon,
                observerEyeM = sentinelGroundM + AppConfig.EYE_HEIGHT_METERS,
                sampler = sampler,
                maxRadiusM = 15_000.0
            )

        assertThat(profile.elevationAnglesDeg.maxOrNull()!!).isGreaterThan(85f)
    }

    // The paired healthy case: once the sentinel is rejected (returned as null) the observer has no DEM
    // ground, the sweep stays flat, and pitching at the sky leaves the frustum → nothing is drawn.
    @Test
    fun rejectedSentinelObserverGround_keepsSkylineFlat() {
        val observerLat = 32.5
        val observerLon = 35.5
        val sampler =
            DemSampler { lat, lon ->
                val distanceM =
                    GeoMathBounds.haversineDistanceM(observerLat, observerLon, lat, lon)
                if (distanceM < 1.0) null else 72f
            }

        val profile =
            computer.sweep(
                observerLat = observerLat,
                observerLon = observerLon,
                observerEyeM = 72.0 + AppConfig.EYE_HEIGHT_METERS,
                sampler = sampler,
                maxRadiusM = 15_000.0
            )

        profile.elevationAnglesDeg.forEach { angle ->
            assertThat(angle.isNaN()).isTrue()
        }
    }

    @Test
    fun missingDemData_returnsInvalidHorizonAngles() {
        val sampler = DemElevationSampler(emptyMap())
        val profile =
            computer.sweep(
                observerLat = 32.5,
                observerLon = 35.5,
                observerEyeM = 100.0,
                sampler = sampler
            )

        profile.elevationAnglesDeg.forEach { angle ->
            assertThat(angle.isNaN()).isTrue()
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
