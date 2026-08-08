package com.nofar.feature.prepare

import com.google.common.truth.Truth.assertThat
import com.nofar.core.visibility.MapVisibilityCellState
import com.nofar.core.visibility.MapVisibilityPreview
import org.junit.Test

class MapVisibilityPreviewMaskRasterTest {
    @Test
    fun rasterBounds_useMaxRegionEdge() {
        val edges = FloatArray(360) { 500f }
        edges[10] = 2_500f
        val preview =
            MapVisibilityPreview.createEmpty(
                observerLat = 32.0,
                observerLon = 35.0,
                regionEdgeMeters = edges,
                maxRadialCells = 25
            )
        assertThat(preview.maxRegionEdgeM()).isWithin(0.1).of(2_500.0)
        assertThat(preview.sampleState(10.5, 50.0)).isEqualTo(MapVisibilityCellState.VISIBLE)
    }
}
