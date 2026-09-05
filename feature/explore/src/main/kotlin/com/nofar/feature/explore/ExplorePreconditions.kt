package com.nofar.feature.explore

import com.nofar.core.model.CompassCalibrationState
import com.nofar.core.model.CoverageSet
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.LocationAccessState

enum class ExploreGate {
    READY,
    WAITING_GPS,
    LOCATION_DENIED,
    CAMERA_DENIED,
    COMPASS_UNAVAILABLE,
    REGION_MISSING,
    REGION_DOWNLOAD_NEEDED,
    REGION_DOWNLOADING,
    GRACE_EXPIRED
}

object ExplorePreconditions {
    fun resolveGate(
        locationAccessState: LocationAccessState,
        waitingForGpsFix: Boolean,
        cameraGranted: Boolean,
        calibrationState: CompassCalibrationState,
        activeCoverageSet: CoverageSet?,
        graceExpired: Boolean,
        simpleModeEnabled: Boolean,
        regionDownloadNeeded: Boolean,
        regionDownloading: Boolean
    ): ExploreGate {
        val permissionGate =
            resolvePermissionGate(
                locationAccessState,
                waitingForGpsFix,
                cameraGranted,
                calibrationState
            )
        return when {
            graceExpired && !simpleModeEnabled -> ExploreGate.GRACE_EXPIRED
            permissionGate != null -> permissionGate
            simpleModeEnabled ->
                resolveSimpleModeGate(regionDownloadNeeded, regionDownloading)
            else -> resolveAdvancedRegionGate(activeCoverageSet)
        }
    }

    /**
     * H-P2-02: the AR overlay (labels + horizon outline) may only be projected when Explore is fully
     * live — READY gate, a real orientation, a measured surface, and non-degraded GPS. Extracted from
     * `ExploreViewModel.reprojectOverlay` so the "gate clears the horizon" invariant is unit-testable
     * without the full ViewModel graph.
     */
    fun canProjectOverlay(
        hasOrientation: Boolean,
        screenWidthPx: Float,
        screenHeightPx: Float,
        gate: ExploreGate,
        locationAccuracyDegraded: Boolean
    ): Boolean = hasOrientation &&
        screenWidthPx > 0f &&
        screenHeightPx > 0f &&
        gate == ExploreGate.READY &&
        !locationAccuracyDegraded

    private fun resolvePermissionGate(
        locationAccessState: LocationAccessState,
        waitingForGpsFix: Boolean,
        cameraGranted: Boolean,
        calibrationState: CompassCalibrationState
    ): ExploreGate? = when {
        locationAccessState == LocationAccessState.DENIED ||
            locationAccessState == LocationAccessState.DENIED_PERMANENTLY -> ExploreGate.LOCATION_DENIED
        locationAccessState == LocationAccessState.NOT_REQUESTED ||
            waitingForGpsFix -> ExploreGate.WAITING_GPS
        !cameraGranted -> ExploreGate.CAMERA_DENIED
        calibrationState == CompassCalibrationState.UNAVAILABLE -> ExploreGate.COMPASS_UNAVAILABLE
        else -> null
    }

    private fun resolveSimpleModeGate(regionDownloadNeeded: Boolean, regionDownloading: Boolean): ExploreGate = when {
        regionDownloading -> ExploreGate.REGION_DOWNLOADING
        regionDownloadNeeded -> ExploreGate.REGION_DOWNLOAD_NEEDED
        else -> ExploreGate.READY
    }

    private fun resolveAdvancedRegionGate(activeCoverageSet: CoverageSet?): ExploreGate =
        if (activeCoverageSet == null ||
            (
                activeCoverageSet.downloadStatus != DownloadStatus.READY &&
                    activeCoverageSet.downloadStatus != DownloadStatus.PARTIAL
                )
        ) {
            ExploreGate.REGION_MISSING
        } else {
            ExploreGate.READY
        }
}
