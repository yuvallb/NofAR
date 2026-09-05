package com.nofar.feature.explore

import com.google.common.truth.Truth.assertThat
import java.util.UUID
import org.junit.Test

class ExploreRouteBuilderTest {
    @Test
    fun parseVirtualSession_roundTripsValidArgs() {
        val coverageSetId = UUID.randomUUID()
        val session =
            ExploreRouteBuilder.parseVirtualSession(
                coverageSetIdRaw = coverageSetId.toString(),
                virtualLatRaw = "32.794",
                virtualLonRaw = "35.531"
            )
        assertThat(session?.primaryRegionId).isEqualTo(coverageSetId)
        assertThat(session?.observerLat).isWithin(0.0001).of(32.794)
        assertThat(session?.observerLon).isWithin(0.0001).of(35.531)
    }

    @Test
    fun parseVirtualSession_rejectsPartialArgs() {
        assertThat(
            ExploreRouteBuilder.parseVirtualSession(
                coverageSetIdRaw = UUID.randomUUID().toString(),
                virtualLatRaw = "",
                virtualLonRaw = "35.0"
            )
        ).isNull()
    }

    @Test
    fun build_includesVirtualCoordinates() {
        val coverageSetId = UUID.randomUUID()
        val route = ExploreRouteBuilder.build(coverageSetId = coverageSetId, virtualLat = 1.0, virtualLon = 2.0)
        assertThat(route).contains("virtualLat=1.0")
        assertThat(route).contains("virtualLon=2.0")
        assertThat(route).contains("coverageSetId=$coverageSetId")
    }
}
