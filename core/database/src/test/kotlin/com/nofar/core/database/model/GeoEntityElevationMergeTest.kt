package com.nofar.core.database.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GeoEntityElevationMergeTest {
    @Test
    fun incomingOsmElevation_winsOverExistingDem() {
        val (elevation, source) =
            GeoEntityElevationMerge.resolve(
                incomingElevation = 120,
                incomingSource = "OSM_TAG",
                existingElevation = 200,
                existingSource = "DEM_SAMPLE"
            )
        assertThat(elevation).isEqualTo(120)
        assertThat(source).isEqualTo("OSM_TAG")
    }

    @Test
    fun nullIncoming_preservesExistingDemSample() {
        val (elevation, source) =
            GeoEntityElevationMerge.resolve(
                incomingElevation = null,
                incomingSource = null,
                existingElevation = 247,
                existingSource = "DEM_SAMPLE"
            )
        assertThat(elevation).isEqualTo(247)
        assertThat(source).isEqualTo("DEM_SAMPLE")
    }

    @Test
    fun bothNull_staysNull() {
        val (elevation, source) =
            GeoEntityElevationMerge.resolve(
                incomingElevation = null,
                incomingSource = null,
                existingElevation = null,
                existingSource = null
            )
        assertThat(elevation).isNull()
        assertThat(source).isNull()
    }
}
