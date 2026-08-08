package com.nofar.core.visibility

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.AppConfig
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.Region
import com.nofar.core.model.RegionBounds
import java.time.Instant
import java.util.UUID
import org.junit.Test

class TerrainViewshedComputerTest {
    private val computer = TerrainViewshedComputer()

    @Test
    fun flatTerrain_visibleToRegionEdge() {
        val observerLat = 32.5
        val observerLon = 35.5
        val eyeM = 100.0 + AppConfig.EYE_HEIGHT_METERS
        val region = testRegion(observerLat, observerLon, radiusM = 3_000.0)
        val sampler =
            DemSampler { _, _ ->
                100f
            }
        val preview =
            computer.compute(
                observerLat = observerLat,
                observerLon = observerLon,
                observerEyeM = eyeM,
                clipRegion = region,
                sampler = sampler
            )!!
        val azimuthIndex = 0
        val lastRadial = preview.radialCellCount(azimuthIndex) - 1
        assertThat(preview.cellState(azimuthIndex, lastRadial)).isEqualTo(MapVisibilityCellState.VISIBLE)
    }

    @Test
    fun steepWall_blocksCellsBeyondWall() {
        val observerLat = 32.5
        val observerLon = 35.5
        val eyeM = 100.0 + AppConfig.EYE_HEIGHT_METERS
        val region = testRegion(observerLat, observerLon, radiusM = 5_000.0)
        val wallDistanceM = 300.0
        val sampler =
            DemSampler { lat, lon ->
                val distanceM = RegionBounds.haversineDistanceM(observerLat, observerLon, lat, lon)
                when {
                    distanceM < wallDistanceM - 50.0 -> 100f
                    distanceM < wallDistanceM + 50.0 -> 800f
                    else -> 100f
                }
            }
        val preview =
            computer.compute(
                observerLat = observerLat,
                observerLon = observerLon,
                observerEyeM = eyeM,
                clipRegion = region,
                sampler = sampler
            )!!
        val wallCell = (wallDistanceM / AppConfig.MAP_PREVIEW_RADIAL_STEP_M).toInt() - 1
        val beyondWallCell = (wallDistanceM / AppConfig.MAP_PREVIEW_RADIAL_STEP_M).toInt()
        assertThat(preview.cellState(0, wallCell)).isEqualTo(MapVisibilityCellState.VISIBLE)
        assertThat(preview.cellState(0, beyondWallCell)).isEqualTo(MapVisibilityCellState.BLOCKED)
    }

    @Test
    fun subTwoMeterSpike_doesNotBlockBeyondTolerance() {
        val observerLat = 32.5
        val observerLon = 35.5
        val eyeM = 100.0 + AppConfig.EYE_HEIGHT_METERS
        val region = testRegion(observerLat, observerLon, radiusM = 2_000.0)
        val spikeDistanceM = 200.0
        val sampler =
            DemSampler { lat, lon ->
                val distanceM = RegionBounds.haversineDistanceM(observerLat, observerLon, lat, lon)
                if (kotlin.math.abs(distanceM - spikeDistanceM) < 40.0) {
                    (100f + AppConfig.MAP_PREVIEW_OCCLUSION_TOLERANCE_M.toFloat() - 0.5f)
                } else {
                    100f
                }
            }
        val preview =
            computer.compute(
                observerLat = observerLat,
                observerLon = observerLon,
                observerEyeM = eyeM,
                clipRegion = region,
                sampler = sampler
            )!!
        val cellIndex = (spikeDistanceM / AppConfig.MAP_PREVIEW_RADIAL_STEP_M).toInt() - 1
        if (cellIndex >= 0) {
            assertThat(preview.cellState(0, cellIndex)).isEqualTo(MapVisibilityCellState.VISIBLE)
        }
    }

    @Test
    fun missingDem_marksUnknownAndStopsRay() {
        val observerLat = 32.5
        val observerLon = 35.5
        val eyeM = 100.0 + AppConfig.EYE_HEIGHT_METERS
        val region = testRegion(observerLat, observerLon, radiusM = 3_000.0)
        val holeStartM = 500.0
        val holeEndM = 700.0
        val sampler =
            DemSampler { lat, lon ->
                val distanceM = RegionBounds.haversineDistanceM(observerLat, observerLon, lat, lon)
                if (distanceM in holeStartM..holeEndM) null else 100f
            }
        val preview =
            computer.compute(
                observerLat = observerLat,
                observerLon = observerLon,
                observerEyeM = eyeM,
                clipRegion = region,
                sampler = sampler
            )!!
        val holeCell = (holeStartM / AppConfig.MAP_PREVIEW_RADIAL_STEP_M).toInt()
        if (holeCell < preview.radialCellCount(0)) {
            assertThat(preview.cellState(0, holeCell)).isEqualTo(MapVisibilityCellState.UNKNOWN)
            assertThat(preview.cellState(0, holeCell + 3)).isEqualTo(MapVisibilityCellState.UNKNOWN)
        }
    }

    @Test
    fun cancelledCompute_returnsNull() {
        val observerLat = 32.5
        val observerLon = 35.5
        val eyeM = 100.0 + AppConfig.EYE_HEIGHT_METERS
        val region = testRegion(observerLat, observerLon, radiusM = 3_000.0)
        var checks = 0
        val preview =
            computer.compute(
                observerLat = observerLat,
                observerLon = observerLon,
                observerEyeM = eyeM,
                clipRegion = region,
                sampler = DemSampler { _, _ -> 100f },
                isCancelled = {
                    checks += 1
                    checks > 3
                }
            )
        assertThat(preview).isNull()
    }

    @Test
    fun sampleState_wrapsAzimuthAcrossZero() {
        val regionEdges = FloatArray(360) { 1_000f }
        val preview =
            MapVisibilityPreview.createEmpty(
                observerLat = 32.0,
                observerLon = 35.0,
                regionEdgeMeters = regionEdges,
                maxRadialCells = 10
            )
        preview.setCellState(0, 0, MapVisibilityCellState.BLOCKED)
        preview.setCellState(359, 0, MapVisibilityCellState.VISIBLE)
        assertThat(preview.sampleState(359.5, 100.0)).isEqualTo(MapVisibilityCellState.VISIBLE)
        assertThat(preview.sampleState(0.5, 100.0)).isEqualTo(MapVisibilityCellState.BLOCKED)
    }

    private fun testRegion(centerLat: Double, centerLon: Double, radiusM: Double): Region = Region(
        id = UUID.randomUUID(),
        name = "Test",
        centerLat = centerLat,
        centerLon = centerLon,
        radiusM = radiusM,
        minLat = centerLat - 0.2,
        maxLat = centerLat + 0.2,
        minLon = centerLon - 0.2,
        maxLon = centerLon + 0.2,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        downloadStatus = DownloadStatus.READY,
        downloadProgressPct = 100,
        osmDatasetVersion = null,
        estimatedSizeBytes = 1L,
        entityCount = 1
    )
}

class RegionRayExtentTest {
    @Test
    fun maxDistanceInsideRegion_atCenter_isPositive() {
        val region =
            Region(
                id = UUID.randomUUID(),
                name = "Test",
                centerLat = 32.0,
                centerLon = 35.0,
                radiusM = 5_000.0,
                minLat = 31.9,
                maxLat = 32.1,
                minLon = 34.9,
                maxLon = 35.1,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
                downloadStatus = DownloadStatus.READY,
                downloadProgressPct = 100,
                osmDatasetVersion = null,
                estimatedSizeBytes = 1L,
                entityCount = 1
            )
        val distance =
            RegionRayExtent.maxDistanceInsideRegionM(
                region = region,
                observerLat = region.centerLat,
                observerLon = region.centerLon,
                bearingDeg = 0.0
            )
        assertThat(distance).isGreaterThan(1_000.0)
    }

    @Test
    fun maxDistanceInsideRegion_fromOffCenter_reachesFarBoundary() {
        val region =
            Region(
                id = UUID.randomUUID(),
                name = "Test",
                centerLat = 32.0,
                centerLon = 35.0,
                radiusM = 10_000.0,
                minLat = 31.8,
                maxLat = 32.2,
                minLon = 34.8,
                maxLon = 35.2,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
                downloadStatus = DownloadStatus.READY,
                downloadProgressPct = 100,
                osmDatasetVersion = null,
                estimatedSizeBytes = 1L,
                entityCount = 1
            )
        val (observerLat, observerLon) =
            GeoMath.destinationPoint(
                lat = region.centerLat,
                lon = region.centerLon,
                bearingDeg = 270.0,
                distanceM = 7_000.0
            )
        val distance =
            RegionRayExtent.maxDistanceInsideRegionM(
                region = region,
                observerLat = observerLat,
                observerLon = observerLon,
                bearingDeg = 90.0
            )

        assertThat(distance).isWithin(25.0).of(17_000.0)
    }
}
