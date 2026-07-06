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
        val bbox = com.nofar.core.model.RegionBounds.boundingBox(32.0, 35.0, 10_000.0)
        assertThat(estimate.demTileCount).isEqualTo(DemTileId.intersectingTiles(bbox).size)
    }
}
