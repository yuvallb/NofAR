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
    fun coordinatesForPoint_usesFloor() {
        assertThat(DemTileId.coordinatesForPoint(32.9, 35.1)).isEqualTo(32 to 35)
    }
}
