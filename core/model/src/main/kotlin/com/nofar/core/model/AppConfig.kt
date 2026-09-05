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

    /** Explore entity spatial query radius around the observer (meters). */
    const val EXPLORE_ENTITY_QUERY_RADIUS_M: Double = 100_000.0

    /** Maximum on-disk DEM bytes for a pack/cell-set offer (fraction of cache limit). */
    const val COVERAGE_BYTE_BUDGET_CACHE_FRACTION: Double = 0.5

    /** Extra headroom added to cache limit when installing a large pack (bytes). */
    const val PACK_CACHE_HEADROOM_BYTES: Long = 200L * 1024 * 1024

    /** Web-mercator-safe latitude clamp for map center (degrees). */
    const val MAP_CENTER_LAT_MIN: Double = -85.0

    /** Web-mercator-safe latitude clamp for map center (degrees). */
    const val MAP_CENTER_LAT_MAX: Double = 85.0

    /** Longitude clamp for map center (degrees). */
    const val MAP_CENTER_LON_MIN: Double = -180.0

    /** Longitude clamp for map center (degrees). */
    const val MAP_CENTER_LON_MAX: Double = 180.0

    /** Horizon ray step within the near field (meters). */
    const val HORIZON_NEAR_FIELD_END_M: Double = 25_000.0

    /** Coarser horizon ray step beyond [HORIZON_NEAR_FIELD_END_M] (meters). */
    const val HORIZON_FAR_RAY_STEP_M: Double = 500.0

    /** Nearest peaks kept when capping visibility candidates. */
    const val PEAK_CANDIDATE_NEAREST_BUDGET: Int = 50

    /** Highest remaining peaks kept after the nearest peak budget. */
    const val PEAK_CANDIDATE_LONG_RANGE_BUDGET: Int = 20

    /** Coarser map viewshed radial step when the preview extent exceeds the near field (meters). */
    const val MAP_PREVIEW_FAR_RADIAL_STEP_M: Double = 500.0

    /** Map viewshed uses the far radial step when max edge exceeds this (meters). */
    const val MAP_PREVIEW_NEAR_FIELD_END_M: Double = 25_000.0

    /** Warn before cellular download when estimated wire size exceeds this (bytes). */
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

    /**
     * When capping candidates, keep up to this many nearest peaks before filling the remainder
     * with nearest places ([VISIBILITY_MAX_CANDIDATES] total).
     */
    const val PEAK_CANDIDATE_BUDGET: Int = 70

    /** Soft budget for a visibility pass (milliseconds); exceeded passes log a warning. */
    const val VISIBILITY_PASS_BUDGET_MS: Long = 200L

    /** GPS altitude is used when vertical accuracy is at or below this threshold (meters). */
    const val GPS_ALTITUDE_ACCURACY_THRESHOLD_METERS: Float = 50f

    /**
     * Explore HUD shows DEM beside GPS when the rounded GPS and DEM altitudes differ by more
     * than this many meters.
     */
    const val ALTITUDE_GPS_DEM_DISAGREE_METERS: Int = 20

    /** AR labels are hidden when horizontal GPS accuracy exceeds this (meters). */
    const val EXPLORE_LOCATION_ACCURACY_THRESHOLD_METERS: Float = 30f

    /** Synthetic horizontal accuracy for Expert virtual observer sessions (meters). */
    const val VIRTUAL_OBSERVER_ACCURACY_METERS: Float = 5f

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

    /** Maximum vertical shelves; off-screen shelves are discarded by the collision resolver. */
    const val EXPLORE_LABEL_SHELF_COUNT: Int = 8

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

    /** Maximum Explore pinch/button zoom ratio; intersected with the camera hardware cap. */
    const val EXPLORE_MAX_ZOOM_RATIO: Float = 5f

    /** Multiplier applied by each discrete Explore zoom button step. */
    const val EXPLORE_ZOOM_BUTTON_STEP: Float = 2f

    /** Azimuth step for the Explore horizon skyline sweep (degrees). Sweep cost ≈ 360/step rays. */
    const val HORIZON_AZIMUTH_STEP_DEG: Float = 2f

    /**
     * Angular step used when reprojecting the cached skyline to screen space (degrees).
     * Independent of [HORIZON_AZIMUTH_STEP_DEG]: the sweep is coarse (budget), the on-screen polyline
     * is sampled finer via circular interpolation of the cached profile.
     */
    const val HORIZON_SCREEN_AZIMUTH_STEP_DEG: Float = 1f

    /**
     * Largest vertical jump (fraction of screen height) allowed *within* one skyline polyline.
     *
     * Vertical-bar mitigation: the sweep is coarse (2° buckets, 150 m ray steps) and picks each ray's
     * max-slope sample, so near-field terrain and DEM coverage edges can make neighbouring azimuths
     * differ by many degrees. Stroking straight through such a step draws the "yellow vertical bar /
     * loop" artifact. Above this delta the polyline is broken instead — a small gap reads as honest
     * missing skyline, a full-height bar reads as a rendering bug. It also covers the case where the
     * skyline dives toward a screen edge just before leaving the vertical frustum (H-DEC-1 Option A).
     */
    const val HORIZON_MAX_SEGMENT_DELTA_Y_FRACTION: Float = 0.15f

    /**
     * Outward sample interval along each horizon azimuth ray (meters).
     * Coarser than [VISIBILITY_RAY_STEP_METERS] (100 m) on purpose: the skyline sweeps ~180 rays per
     * pass, so a 150 m step keeps sample count ≈ collectionRadius/step + 1 within the §8 visibility
     * budget (worst case ≈ floor(25_000 / 150) + 1 = 167 samples per ray).
     */
    const val HORIZON_RAY_STEP_M: Double = 150.0

    /**
     * Multiplier applied to boundary radius-of-gyration when approximating place footprint (Prepare).
     */
    const val FOOTPRINT_RADIUS_GYRATION_FACTOR: Double = 1.15

    /** Minimum stored footprint radius for places with boundary geometry (meters). */
    const val FOOTPRINT_RADIUS_MIN_M: Double = 200.0

    /** Maximum stored footprint radius — caps metro-scale boundaries (meters). */
    const val FOOTPRINT_RADIUS_MAX_M: Double = 15_000.0

    /** Max boundary vertices sampled when computing footprint radius at Prepare time. */
    const val FOOTPRINT_BOUNDARY_MAX_POINTS: Int = 2_048

    /** Minimum angular diameter before a place renders as an area instead of a point (degrees). */
    const val EXPLORE_FOOTPRINT_MIN_ANGULAR_DEG: Double = 4.0

    /** Treat the user as standing on a peak when within this distance of its point (meters). */
    const val EXPLORE_HERE_PEAK_RADIUS_M: Double = 200.0

    /** Azimuth step for expert virtual-location map viewshed (degrees). */
    const val MAP_PREVIEW_AZIMUTH_STEP_DEG: Float = 1f

    /** Radial step along each viewshed ray (meters). */
    const val MAP_PREVIEW_RADIAL_STEP_M: Double = 100.0

    /** Vertical clearance before terrain counts as blocking in map viewshed preview (meters). */
    const val MAP_PREVIEW_OCCLUSION_TOLERANCE_M: Double = 2.0

    /** Square mask raster size for map viewshed overlay (pixels). */
    const val MAP_PREVIEW_MASK_SIZE_PX: Int = 512

    /** Maximum azimuth/pitch offset applied automatically after skyline matching (degrees). */
    const val HORIZON_ALIGN_MAX_AUTO_DEG: Float = 8f

    /** Grid-search half-range for azimuth offset during skyline matching (degrees). */
    const val HORIZON_ALIGN_AZIMUTH_SEARCH_DEG: Float = 12f

    /** Grid-search half-range for pitch offset during skyline matching (degrees). */
    const val HORIZON_ALIGN_PITCH_SEARCH_DEG: Float = 8f

    /** Grid-search step for azimuth and pitch offsets (degrees). */
    const val HORIZON_ALIGN_SEARCH_STEP_DEG: Float = 0.25f

    /** Number of horizontal samples when comparing camera and DEM skylines. */
    const val HORIZON_ALIGN_PROFILE_COLUMNS: Int = 160

    /** Minimum fraction of columns with valid camera and DEM samples to attempt matching. */
    const val HORIZON_ALIGN_MIN_VALID_COLUMN_FRACTION: Float = 0.5f

    /** Minimum normalized Y variance in the DEM profile (flat sea/plain rejection). */
    const val HORIZON_ALIGN_MIN_DEM_Y_VARIANCE: Float = 0.0005f

    /** Best match must beat the zero-offset baseline by at least this fraction. */
    const val HORIZON_ALIGN_MIN_IMPROVEMENT_FRACTION: Float = 0.15f

    /** |cameraElevationDeg| must be below this to attempt automatic alignment. */
    const val HORIZON_ALIGN_MAX_CAMERA_ELEVATION_DEG: Float = 20f

    /** Orientation must stay within these deltas (degrees) for this long before matching. */
    const val HORIZON_ALIGN_STILL_AZIMUTH_DEG: Float = 1.5f
    const val HORIZON_ALIGN_STILL_PITCH_DEG: Float = 1.5f
    const val HORIZON_ALIGN_STILL_ROLL_DEG: Float = 3f
    const val HORIZON_ALIGN_STILL_DWELL_MS: Long = 1_000L

    /** Minimum interval between automatic alignment attempts (milliseconds). */
    const val HORIZON_ALIGN_ATTEMPT_COOLDOWN_MS: Long = 10_000L

    /** Minimum vertical luminance step to treat a camera column as having a skyline edge. */
    const val HORIZON_ALIGN_MIN_EDGE_CONTRAST: Int = 12
}
