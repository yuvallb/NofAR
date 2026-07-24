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

    /** Default circular region radius for Simple Mode auto-download (meters). */
    const val SIMPLE_MODE_DEFAULT_RADIUS_M: Double = 10_000.0

    /**
     * Extra radius beyond [Region.radiusM] used for OSM/DEM download and Explore visibility.
     * Membership / "inside region" checks stay on [Region.radiusM].
     */
    const val DATA_COLLECTION_RADIUS_PADDING_M: Double = 5_000.0

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

    /** Ray-march sample interval along terrain profile (meters). */
    const val VISIBILITY_RAY_STEP_METERS: Double = 100.0

    /** Maximum candidate entities passed to the visibility engine per pass. */
    const val VISIBILITY_MAX_CANDIDATES: Int = 100

    /** GPS altitude is used when vertical accuracy is at or below this threshold (meters). */
    const val GPS_ALTITUDE_ACCURACY_THRESHOLD_METERS: Float = 50f

    /** AR labels are hidden when horizontal GPS accuracy exceeds this (meters). */
    const val EXPLORE_LOCATION_ACCURACY_THRESHOLD_METERS: Float = 30f

    /** Mean Earth radius for haversine and curvature correction (meters). */
    const val EARTH_RADIUS_METERS: Double = 6_371_000.0

    /**
     * Standard atmospheric refraction coefficient for line-of-sight curvature correction.
     * Effective drop = d² / (2R) × (1 − k). Matches Requirements §3.3.3.
     */
    const val ATMOSPHERIC_REFRACTION_COEFFICIENT: Double = 0.13

    /** Effective Earth radius including refraction: R / (1 − k). */
    const val EFFECTIVE_EARTH_RADIUS_METERS: Double = 7_322_988.505747126

    /** Horizontal bucket width for Explore label group ids (pixels). */
    const val EXPLORE_CLUSTER_BUCKET_WIDTH_PX: Int = 50

    /** Number of vertical shelves for the 1D label collision resolver. */
    const val EXPLORE_LABEL_SHELF_COUNT: Int = 5

    /** Vertical pitch between successive label shelves (pixels). Must exceed card height + pads. */
    const val EXPLORE_LABEL_SHELF_PITCH_PX: Int = 128

    /** Minimum gap from card bottom to terrain anchor (pixels). */
    const val EXPLORE_LABEL_LEADER_GAP_PX: Int = 28

    /** Estimated average character width for label card AABB (pixels). */
    const val EXPLORE_LABEL_CHAR_WIDTH_PX: Int = 12

    /** Horizontal padding included in estimated label card width (pixels). */
    const val EXPLORE_LABEL_HORIZONTAL_PADDING_PX: Int = 32

    /** Minimum estimated label card width (pixels). */
    const val EXPLORE_LABEL_MIN_WIDTH_PX: Int = 96

    /** Maximum estimated label card width (pixels). */
    const val EXPLORE_LABEL_MAX_WIDTH_PX: Int = 280

    /** Extra horizontal padding when testing card AABB overlap (pixels). */
    const val EXPLORE_LABEL_COLLISION_PAD_PX: Int = 12

    /** Extra vertical gap reserved between stacked card AABBs (pixels). */
    const val EXPLORE_LABEL_VERTICAL_GAP_PX: Int = 8

    /** Estimated label card height for AABB collision and on-screen clamping (pixels). */
    const val EXPLORE_LABEL_ESTIMATED_HEIGHT_PX: Int = 100

    /** Fallback horizontal FOV when camera characteristics are unavailable (degrees). */
    const val CAMERA_HORIZONTAL_FOV_FALLBACK_DEG: Float = 60f

    /** Fallback vertical FOV when camera characteristics are unavailable (degrees). */
    const val CAMERA_VERTICAL_FOV_FALLBACK_DEG: Float = 45f

    /** Azimuth step for the Explore horizon skyline sweep (degrees). */
    const val HORIZON_AZIMUTH_STEP_DEG: Float = 2f

    /** Outward sample interval along each horizon azimuth ray (meters). */
    const val HORIZON_RAY_STEP_M: Double = 150.0

    /** Maximum radius for the horizon skyline sweep (meters). */
    const val HORIZON_MAX_RADIUS_M: Double = 15_000.0

    /**
     * Extra azimuth padding beyond the horizontal FOV when reprojecting the cached horizon profile.
     * Keeps the polyline continuous at screen edges.
     */
    const val HORIZON_SCREEN_PADDING_DEG: Float = 4f
}
