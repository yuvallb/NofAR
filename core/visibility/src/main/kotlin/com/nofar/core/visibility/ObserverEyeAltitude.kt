package com.nofar.core.visibility

import com.nofar.core.model.AppConfig

/** Where the resolved observer eye altitude came from. Surfaced in the Explore debug overlay. */
enum class ObserverEyeSource { DEM, GPS }

/** Observer eye altitude (meters) plus the source used to resolve it. */
data class ObserverEye(val eyeM: Double, val source: ObserverEyeSource)

/**
 * Single source of truth for the observer eye altitude used by terrain sweeps.
 *
 * Policy: when a local DEM ground sample exists the eye is **DEM-relative**
 * (`demGround + EYE_HEIGHT_METERS`). This keeps skyline elevation angles consistent with the terrain
 * the ray samples — on flat ground the outline sits on the true horizon instead of drifting up or
 * down when GPS altitude disagrees with the DEM. Only when no DEM ground is available does it fall
 * back to GPS altitude (`observerElevationM + EYE_HEIGHT_METERS`).
 *
 * Entity line-of-sight (`DemRaycastVisibilityEngine`) intentionally keeps the GPS-priority observer
 * elevation from [ObserverElevationResolver] (Requirements §4.6). Under a GPS/DEM altitude offset the
 * label and outline eyes can therefore differ; see `DemRaycastVisibilityEngineTest` for the documented
 * Δangle. Switching labels to the DEM-aligned eye is a product decision left to a future change.
 */
object ObserverEyeAltitude {
    fun resolve(observerElevationM: Double, demGroundM: Float?): ObserverEye {
        val demGround = demGroundM?.toDouble()
        return if (demGround != null) {
            ObserverEye(eyeM = demGround + AppConfig.EYE_HEIGHT_METERS, source = ObserverEyeSource.DEM)
        } else {
            ObserverEye(eyeM = observerElevationM + AppConfig.EYE_HEIGHT_METERS, source = ObserverEyeSource.GPS)
        }
    }
}
