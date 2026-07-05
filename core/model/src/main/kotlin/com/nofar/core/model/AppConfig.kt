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

    /** Keep Explore running after leaving the active region (GPS excursion tolerance). */
    val exploreRegionExitGracePeriod: Duration = 2.minutes

    /** GPS update interval for Explore/Home (milliseconds). Requirements §8: ≥ 1 s average. */
    const val GPS_UPDATE_INTERVAL_MS: Long = 1_000L

    /** Minimum interval between GPS callbacks (milliseconds). */
    const val GPS_MIN_UPDATE_INTERVAL_MS: Long = 1_000L

    /** Recompute magnetic declination when the user moves at least this far (meters). */
    const val DECLINATION_UPDATE_DISTANCE_METERS: Double = 1_000.0

    /**
     * One Euro Filter defaults (Casiez et al., CHI 2012).
     * Tune via debug overlay in Explore (Requirements §13).
     */
    const val ONE_EURO_MIN_CUTOFF_AZIMUTH: Double = 1.0
    const val ONE_EURO_BETA_AZIMUTH: Double = 0.007
    const val ONE_EURO_MIN_CUTOFF_PITCH: Double = 1.0
    const val ONE_EURO_BETA_PITCH: Double = 0.007
    const val ONE_EURO_MIN_CUTOFF_ROLL: Double = 1.0
    const val ONE_EURO_BETA_ROLL: Double = 0.007

    /** Default derivative smoothing factor for the One Euro Filter. */
    const val ONE_EURO_D_CUTOFF: Double = 1.0

    /**
     * Compass accuracy at or below this [android.hardware.SensorManager] level triggers calibration UX.
     * SENSOR_STATUS_ACCURACY_LOW = 1.
     */
    const val COMPASS_ACCURACY_THRESHOLD: Int = 1
}
