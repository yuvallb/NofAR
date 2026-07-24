package com.nofar.core.model

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
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
        assertEquals(2.seconds, AppConfig.visibilityRefreshMaxInterval)
        assertEquals(2.minutes, AppConfig.exploreRegionExitGracePeriod)
        assertEquals(1_000L, AppConfig.GPS_UPDATE_INTERVAL_MS)
        assertEquals(1_000.0, AppConfig.DECLINATION_UPDATE_DISTANCE_METERS, 0.001)
        assertEquals(30f, AppConfig.EXPLORE_LOCATION_ACCURACY_THRESHOLD_METERS, 0.001f)
    }
}
