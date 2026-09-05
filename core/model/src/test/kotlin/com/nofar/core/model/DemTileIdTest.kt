package com.nofar.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DemTileIdTest {
    @Test
    fun fromCoordinates_formatsGlo90TileId() {
        val tileId = DemTileId.fromCoordinates(32, 35)
        assertThat(tileId).isEqualTo("Copernicus_DSM_COG_30_N32_00_E035_00_DEM")
        assertThat(DemTileId.parse(tileId)).isEqualTo(32 to 35)
    }

    @Test
    fun intersectingTiles_returnsAllCellsInBbox() {
        val bbox = BoundingBox(minLat = 32.1, maxLat = 33.2, minLon = 34.8, maxLon = 35.9)
        val tiles = DemTileId.intersectingTiles(bbox)
        assertThat(tiles).containsExactly(32 to 34, 32 to 35, 33 to 34, 33 to 35)
    }

    @Test
    fun localCellRing_returns3x3AroundObserver() {
        val ring = DemTileId.localCellRing(32.5, 35.5)
        assertThat(ring).hasSize(9)
        assertThat(ring).contains(32 to 35)
        assertThat(ring).contains(31 to 34)
        assertThat(ring).contains(33 to 36)
    }

    @Test
    fun localCellRing_wrapsDateline() {
        val ring = DemTileId.localCellRing(10.5, 179.5)
        assertThat(ring).contains(10 to 179)
        assertThat(ring).contains(10 to -180)
        assertThat(ring).doesNotContain(10 to 180)
        assertThat(ring).doesNotContain(10 to 181)
    }

    @Test
    fun localCellRing_clampsNorthPole() {
        val ring = DemTileId.localCellRing(89.5, 10.5)
        assertThat(ring.map { it.first }.maxOrNull()).isAtMost(89)
        assertThat(ring).doesNotContain(90 to 10)
    }
}
