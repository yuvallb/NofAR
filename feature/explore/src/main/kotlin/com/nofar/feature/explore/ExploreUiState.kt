package com.nofar.feature.explore

import com.nofar.core.data.usecase.ExploreRegionResolution
import com.nofar.core.data.usecase.QuickRegionProposal
import com.nofar.core.designsystem.component.ArLabel
import com.nofar.core.model.AltitudeReading
import com.nofar.core.model.CompassCalibrationState
import com.nofar.core.model.LocationAccessState
import com.nofar.core.model.Region
import com.nofar.core.visibility.CameraFieldOfView
import com.nofar.core.visibility.ClusteredLabel

data class ExploreUiState(
    val compassBearingDeg: Float = 0f,
    val altitude: AltitudeReading? = null,
    val calibrationState: CompassCalibrationState = CompassCalibrationState.UNAVAILABLE,
    val locationAccessState: LocationAccessState = LocationAccessState.NOT_REQUESTED,
    val waitingForGpsFix: Boolean = false,
    val locationAccuracyMeters: Float? = null,
    val locationAccuracyDegraded: Boolean = false,
    val cameraGranted: Boolean = false,
    val exploreGate: ExploreGate = ExploreGate.WAITING_GPS,
    val simpleModeEnabled: Boolean = false,
    val activeRegion: Region? = null,
    val activeRegions: List<Region> = emptyList(),
    val activeRegionName: String? = null,
    val partialRegionWarning: Boolean = false,
    val regionResolution: ExploreRegionResolution? = null,
    val downloadPrompt: QuickRegionProposal? = null,
    val downloadPromptDismissed: Boolean = false,
    val downloadProgressPct: Int = 0,
    val downloadUiMessage: String? = null,
    val showCellularWarning: Boolean = false,
    val showWifiOnlyBlocked: Boolean = false,
    val clusteredLabels: List<ClusteredLabel> = emptyList(),
    val arLabels: List<ArLabel> = emptyList(),
    val expandedBucketIndex: Int? = null,
    val expandedCluster: ClusteredLabel? = null,
    val showRegionExitBanner: Boolean = false,
    val regionExitGraceSecondsRemaining: Int = 0,
    val showGraceExpiredDialog: Boolean = false,
    val showNoVisibleEntitiesHint: Boolean = false,
    val cameraFov: CameraFieldOfView = CameraFieldOfView.fallback(),
    val screenWidthPx: Float = 0f,
    val screenHeightPx: Float = 0f,
    val debugRawAzimuthDeg: Float? = null,
    val debugSmoothedAzimuthDeg: Float? = null,
    val useRawSensorOverlay: Boolean = false,
    val visibleEntityCount: Int = 0
)
