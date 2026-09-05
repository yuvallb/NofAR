package com.nofar.core.data.prepare

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.DemTileId
import org.junit.Test

class PrepareEstimatorTest {
    @Test
    fun estimate_returnsPositiveSizeAndTileCount() {
        val estimate = PrepareEstimator.estimate(32.0, 35.0, 10_000.0)
        assertThat(estimate.osmEstimateBytes).isGreaterThan(0L)
        assertThat(estimate.demTileCount).isAtLeast(1)
        assertThat(estimate.totalEstimateBytes).isGreaterThan(estimate.osmEstimateBytes)
    }

    @Test
    fun estimate_tileCountMatchesIntersectingTiles() {
        val estimate = PrepareEstimator.estimate(32.0, 35.0, 10_000.0)
        val bbox = com.nofar.core.model.GeoMathBounds.boundingBox(32.0, 35.0, 10_000.0)
        assertThat(estimate.demTileCount).isEqualTo(DemTileId.intersectingTiles(bbox).size)
    }

    @Test
    fun estimateForCells_localRingIsDemDominatedAround26Mb() {
        val cells = DemTileId.localCellRing(32.5, 35.5)
        val estimate = PrepareEstimator.estimateForCells(cells)
        // 9 × ~2.9 MB DEM disk ≈ 26 MB; OSM for place/peak only is small.
        assertThat(estimate.demTileCount).isEqualTo(9)
        assertThat(estimate.demEstimateMinBytes).isAtLeast(20L * 1024 * 1024)
        assertThat(estimate.demEstimateMinBytes).isAtMost(40L * 1024 * 1024)
        assertThat(estimate.osmEstimateBytes).isLessThan(estimate.demEstimateMinBytes)
        assertThat(estimate.totalEstimateBytes).isAtMost(50L * 1024 * 1024)
    }
}
