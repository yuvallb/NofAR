package com.nofar.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeNavigationTest {
    @Test
    fun addRegionRoute_usesBlankPreparePath() {
        assertEquals("prepare?regionId=", buildPrepareRoute(null))
    }

    @Test
    fun existingRegionRoute_includesRegionId() {
        val regionId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001")
        assertEquals(
            "prepare?regionId=00000000-0000-0000-0000-000000000001",
            buildPrepareRoute(regionId)
        )
    }
}

/** Mirrors [com.nofar.app.NofARNavHost] prepare navigation. */
internal fun buildPrepareRoute(regionId: java.util.UUID?): String {
    val template = "prepare?regionId={regionId}"
    return if (regionId == null) {
        template.replace("{regionId}", "")
    } else {
        template.replace("{regionId}", regionId.toString())
    }
}
