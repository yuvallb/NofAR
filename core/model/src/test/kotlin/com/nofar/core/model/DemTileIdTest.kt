package com.nofar.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DemTileIdTest {
    @Test
    fun fromCoordinates_roundTrips() {
        val tileId = DemTileId.fromCoordinates(32, 35)
        assertThat(tileId).isEqualTo("Copernicus_DSM_COG_10_N32_00_E035_00_DEM")
        assertThat(DemTileId.parse(tileId)).isEqualTo(32 to 35)
    }

    @Test
    fun intersectingTiles_coversBoundingBox() {
        val bbox = com.nofar.core.model.BoundingBox(31.2, 34.1, 33.8, 36.4)
        val tiles = DemTileId.intersectingTiles(bbox)
        assertThat(tiles).contains(32 to 35)
        assertThat(tiles).isNotEmpty()
    }
}
