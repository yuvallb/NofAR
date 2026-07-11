package com.nofar.core.network

import com.nofar.core.network.OverpassConfig.mirrorBaseUrls
import org.junit.Assert.assertEquals
import org.junit.Test

class OverpassConfigTest {
    @Test
    fun mirrorListHasThreeTiers() {
        assertEquals(3, mirrorBaseUrls.size)
        assertEquals("https://osm.hpi.de/overpass/api/interpreter", mirrorBaseUrls[0])
        assertEquals("https://overpass-api.de/api/interpreter", mirrorBaseUrls[1])
        assertEquals(
            "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
            mirrorBaseUrls[2]
        )
    }
}
