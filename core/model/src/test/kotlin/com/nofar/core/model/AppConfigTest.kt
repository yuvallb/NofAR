package com.nofar.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolutionLevelTest {
    @Test
    fun mediumIncludesPeaksAndCorePlaceTags() {
        val level = ResolutionLevel.Medium
        assertTrue(level.includesPeaks)
        assertEquals(setOf("city", "town", "village"), level.placeTags)
    }

    @Test
    fun basicExcludesPeaks() {
        assertFalse(ResolutionLevel.Basic.includesPeaks)
    }
}

class AppConfigTest {
    @Test
    fun defaultsMatchRequirements() {
        assertEquals(ResolutionLevel.Medium, AppConfig.defaultResolutionLevel)
        assertEquals(1.7, AppConfig.EYE_HEIGHT_METERS, 0.001)
        assertEquals(20.0, AppConfig.VISIBILITY_REFRESH_DISTANCE_METERS, 0.001)
        assertEquals(5.0, AppConfig.REGION_RADIUS_MIN_KM, 0.001)
        assertEquals(20.0, AppConfig.REGION_RADIUS_MAX_KM, 0.001)
        assertEquals(50L * 1024 * 1024, AppConfig.CELLULAR_DOWNLOAD_WARNING_BYTES)
        assertEquals(500L * 1024 * 1024, AppConfig.DEM_CACHE_DEFAULT_LIMIT_BYTES)
    }
}
