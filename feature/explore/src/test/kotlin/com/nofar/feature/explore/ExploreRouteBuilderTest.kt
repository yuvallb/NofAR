package com.nofar.feature.explore

import com.google.common.truth.Truth.assertThat
import java.util.UUID
import org.junit.Test

class ExploreRouteBuilderTest {
    @Test
    fun parseVirtualSession_roundTripsValidArgs() {
        val regionId = UUID.randomUUID()
        val session =
            ExploreRouteBuilder.parseVirtualSession(
                regionIdRaw = regionId.toString(),
                virtualLatRaw = "32.794",
                virtualLonRaw = "35.531"
            )
        assertThat(session?.primaryRegionId).isEqualTo(regionId)
        assertThat(session?.observerLat).isWithin(0.0001).of(32.794)
        assertThat(session?.observerLon).isWithin(0.0001).of(35.531)
    }

    @Test
    fun parseVirtualSession_rejectsPartialArgs() {
        assertThat(
            ExploreRouteBuilder.parseVirtualSession(
                regionIdRaw = UUID.randomUUID().toString(),
                virtualLatRaw = "",
                virtualLonRaw = "35.0"
            )
        ).isNull()
    }

    @Test
    fun build_includesVirtualCoordinates() {
        val regionId = UUID.randomUUID()
        val route = ExploreRouteBuilder.build(regionId = regionId, virtualLat = 1.0, virtualLon = 2.0)
        assertThat(route).contains("virtualLat=1.0")
        assertThat(route).contains("virtualLon=2.0")
        assertThat(route).contains("regionId=$regionId")
    }
}
