package com.nofar.core.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Application-wide defaults per Requirements §3.3.1 and §8.
 */
object AppConfig {
    val defaultResolutionLevel: ResolutionLevel = ResolutionLevel.Medium

    /** Observer eye height above ground for elevation-angle calculations (meters). */
    const val EYE_HEIGHT_METERS: Double = 1.7

    /** Re-run visibility pass when the user moves at least this far (meters). */
    const val VISIBILITY_REFRESH_DISTANCE_METERS: Double = 20.0

    /** Maximum interval between visibility passes. */
    val visibilityRefreshMaxInterval: Duration = 2.seconds

    /** Minimum circular region radius (kilometers). */
    const val REGION_RADIUS_MIN_KM: Double = 5.0

    /** Maximum circular region radius (kilometers). */
    const val REGION_RADIUS_MAX_KM: Double = 20.0

    /** Warn before cellular download when estimated size exceeds this (bytes). */
    const val CELLULAR_DOWNLOAD_WARNING_BYTES: Long = 50L * 1024 * 1024

    /** Default DEM tile cache size limit (bytes). */
    const val DEM_CACHE_DEFAULT_LIMIT_BYTES: Long = 500L * 1024 * 1024

    /** Grace period before evicting unused DEM tiles after region delete. */
    val demCacheGracePeriod: Duration = 2.minutes
}
