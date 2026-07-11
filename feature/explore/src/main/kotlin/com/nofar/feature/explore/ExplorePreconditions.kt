package com.nofar.feature.explore

import com.nofar.core.model.CompassCalibrationState
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.LocationAccessState
import com.nofar.core.model.Region

enum class ExploreGate {
    READY,
    WAITING_GPS,
    LOCATION_DENIED,
    CAMERA_DENIED,
    COMPASS_UNAVAILABLE,
    REGION_MISSING,
    REGION_OUTSIDE,
    REGION_DOWNLOAD_NEEDED,
    REGION_DOWNLOADING,
    REGION_DOWNLOAD_DISMISSED,
    GRACE_EXPIRED
}

object ExplorePreconditions {
    fun resolveGate(
        locationAccessState: LocationAccessState,
        waitingForGpsFix: Boolean,
        cameraGranted: Boolean,
        calibrationState: CompassCalibrationState,
        activeRegion: Region?,
        graceExpired: Boolean,
        simpleModeEnabled: Boolean,
        regionDownloadNeeded: Boolean,
        regionDownloading: Boolean,
        downloadPromptDismissed: Boolean
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
                resolveSimpleModeGate(regionDownloadNeeded, regionDownloading, downloadPromptDismissed)
            else -> resolveAdvancedRegionGate(activeRegion)
        }
    }

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

    private fun resolveSimpleModeGate(
        regionDownloadNeeded: Boolean,
        regionDownloading: Boolean,
        downloadPromptDismissed: Boolean
    ): ExploreGate = when {
        regionDownloading -> ExploreGate.REGION_DOWNLOADING
        regionDownloadNeeded && !downloadPromptDismissed -> ExploreGate.REGION_DOWNLOAD_NEEDED
        regionDownloadNeeded && downloadPromptDismissed -> ExploreGate.REGION_DOWNLOAD_DISMISSED
        else -> ExploreGate.READY
    }

    private fun resolveAdvancedRegionGate(activeRegion: Region?): ExploreGate = if (activeRegion == null ||
        (
            activeRegion.downloadStatus != DownloadStatus.READY &&
                activeRegion.downloadStatus != DownloadStatus.PARTIAL
            )
    ) {
        ExploreGate.REGION_MISSING
    } else {
        ExploreGate.READY
    }
}
