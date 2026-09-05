package com.nofar.core.visibility

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.AppConfig
import com.nofar.core.model.CellMembership
import com.nofar.core.model.GeoMathBounds
import org.junit.Test

class TerrainViewshedComputerTest {
    private val computer = TerrainViewshedComputer()

    @Test
    fun flatTerrain_visibleToHorizonEdge() {
        val observerLat = 32.5
        val observerLon = 35.5
        val eyeM = 100.0 + AppConfig.EYE_HEIGHT_METERS
        val clipCellIds = testCellIds(observerLat, observerLon)
        val sampler = DemSampler { _, _ -> 100f }
        val preview =
            computer.compute(
                observerLat = observerLat,
                observerLon = observerLon,
                observerEyeM = eyeM,
                clipCellIds = clipCellIds,
                sampler = sampler
            )!!
        val azimuthIndex = 0
        assertThat(preview.cellState(azimuthIndex, 0)).isEqualTo(MapVisibilityCellState.VISIBLE)
        assertThat(preview.radialCellCount(azimuthIndex)).isGreaterThan(0)
    }

    @Test
    fun steepWall_blocksCellsBeyondWall() {
        val observerLat = 32.5
        val observerLon = 35.5
        val eyeM = 100.0 + AppConfig.EYE_HEIGHT_METERS
        val clipCellIds = testCellIds(observerLat, observerLon)
        val wallDistanceM = 1_750.0
        val sampler =
            DemSampler { lat, lon ->
                val distanceM = GeoMathBounds.haversineDistanceM(observerLat, observerLon, lat, lon)
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
                clipCellIds = clipCellIds,
                sampler = sampler
            )!!
        val blockedExists =
            (0 until preview.radialCellCount(0)).any { radial ->
                preview.cellState(0, radial) == MapVisibilityCellState.BLOCKED
            }
        assertThat(blockedExists).isTrue()
    }

    @Test
    fun subTwoMeterSpike_doesNotBlockBeyondTolerance() {
        val observerLat = 32.5
        val observerLon = 35.5
        val eyeM = 100.0 + AppConfig.EYE_HEIGHT_METERS
        val clipCellIds = testCellIds(observerLat, observerLon)
        val spikeDistanceM = 200.0
        val sampler =
            DemSampler { lat, lon ->
                val distanceM = GeoMathBounds.haversineDistanceM(observerLat, observerLon, lat, lon)
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
                clipCellIds = clipCellIds,
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
        val clipCellIds = testCellIds(observerLat, observerLon)
        val holeStartM = 500.0
        val holeEndM = 700.0
        val sampler =
            DemSampler { lat, lon ->
                val distanceM = GeoMathBounds.haversineDistanceM(observerLat, observerLon, lat, lon)
                if (distanceM in holeStartM..holeEndM) null else 100f
            }
        val preview =
            computer.compute(
                observerLat = observerLat,
                observerLon = observerLon,
                observerEyeM = eyeM,
                clipCellIds = clipCellIds,
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
        val clipCellIds = testCellIds(observerLat, observerLon)
        var checks = 0
        val preview =
            computer.compute(
                observerLat = observerLat,
                observerLon = observerLon,
                observerEyeM = eyeM,
                clipCellIds = clipCellIds,
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

    private fun testCellIds(lat: Double, lon: Double): Set<String> = setOf(CellMembership.cellIdForPoint(lat, lon))
}

class CoverageRayExtentTest {
    @Test
    fun maxDistanceInsideAnyRegionM_whenInsideCell_returnsHorizonRadius() {
        val observerLat = 32.5
        val observerLon = 35.5
        val cellIds = setOf(CellMembership.cellIdForPoint(observerLat, observerLon))
        val distance =
            CoverageRayExtent.maxDistanceInsideAnyRegionM(
                cellIds = cellIds,
                observerLat = observerLat,
                observerLon = observerLon,
                bearingDeg = 0.0
            )
        assertThat(distance).isEqualTo(AppConfig.EXPLORE_ENTITY_QUERY_RADIUS_M)
    }

    @Test
    fun maxDistanceInsideAnyRegionM_whenOutsideCells_returnsZero() {
        val distance =
            CoverageRayExtent.maxDistanceInsideAnyRegionM(
                cellIds = setOf(CellMembership.cellIdForPoint(40.0, 40.0)),
                observerLat = 32.5,
                observerLon = 35.5,
                bearingDeg = 0.0
            )
        assertThat(distance).isEqualTo(0.0)
    }
}
