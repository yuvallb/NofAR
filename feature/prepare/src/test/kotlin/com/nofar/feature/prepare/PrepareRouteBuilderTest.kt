package com.nofar.feature.prepare

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PrepareRouteBuilderTest {
    @Test
    fun build_encodesNameAndCoords() {
        val route =
            PrepareRouteBuilder.build(
                centerLat = 32.1,
                centerLon = 35.2,
                radiusM = 10_000.0,
                name = "Near Peak"
            )
        assertThat(route).contains("centerLat=32.1")
        assertThat(route).contains("centerLon=35.2")
        assertThat(route).contains("radiusM=10000.0")
        assertThat(route).contains("name=Near+Peak")
    }

    @Test
    fun parseName_decodesUri() {
        assertThat(PrepareRouteBuilder.parseName("Near+Peak")).isEqualTo("Near Peak")
        assertThat(PrepareRouteBuilder.parseName("Near%20Peak")).isEqualTo("Near Peak")
    }
}
