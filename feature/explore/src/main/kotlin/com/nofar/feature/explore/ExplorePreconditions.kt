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
    GRACE_EXPIRED
}

object ExplorePreconditions {
    fun resolveGate(
        locationAccessState: LocationAccessState,
        waitingForGpsFix: Boolean,
        cameraGranted: Boolean,
        calibrationState: CompassCalibrationState,
        activeRegion: Region?,
        graceExpired: Boolean
    ): ExploreGate = when {
        graceExpired -> ExploreGate.GRACE_EXPIRED
        locationAccessState == LocationAccessState.DENIED ||
            locationAccessState == LocationAccessState.DENIED_PERMANENTLY -> ExploreGate.LOCATION_DENIED
        locationAccessState == LocationAccessState.NOT_REQUESTED ||
            waitingForGpsFix -> ExploreGate.WAITING_GPS
        !cameraGranted -> ExploreGate.CAMERA_DENIED
        calibrationState == CompassCalibrationState.UNAVAILABLE -> ExploreGate.COMPASS_UNAVAILABLE
        activeRegion == null ||
            (
                activeRegion.downloadStatus != DownloadStatus.READY &&
                    activeRegion.downloadStatus != DownloadStatus.PARTIAL
                ) -> ExploreGate.REGION_MISSING
        else -> ExploreGate.READY
    }
}
