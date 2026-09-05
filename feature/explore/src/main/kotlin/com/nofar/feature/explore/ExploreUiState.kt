package com.nofar.feature.explore

import com.nofar.core.data.usecase.ExploreCoverageResolution
import com.nofar.core.data.usecase.QuickCoverageProposal
import com.nofar.core.designsystem.component.ArLabel
import com.nofar.core.designsystem.component.HorizonOutlinePoint
import com.nofar.core.model.AltitudeReading
import com.nofar.core.model.CompassCalibrationState
import com.nofar.core.model.CoverageSet
import com.nofar.core.model.LocationAccessState
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
    val activeCoverageSet: CoverageSet? = null,
    val activeCoverageSets: List<CoverageSet> = emptyList(),
    val membershipCoverageSets: List<CoverageSet> = emptyList(),
    val activeCellIds: Set<String> = emptySet(),
    val activeCoverageSetName: String? = null,
    val partialRegionWarning: Boolean = false,
    val regionResolution: ExploreCoverageResolution? = null,
    val downloadPrompt: QuickCoverageProposal? = null,
    val downloadProgressPct: Int = 0,
    val downloadUiMessage: String? = null,
    val showCellularWarning: Boolean = false,
    val showWifiOnlyBlocked: Boolean = false,
    val clusteredLabels: List<ClusteredLabel> = emptyList(),
    val arLabels: List<ArLabel> = emptyList(),
    val horizonLineSegments: List<List<HorizonOutlinePoint>> = emptyList(),
    val showHorizonOutline: Boolean = true,
    val showLabelElevation: Boolean = false,
    val expandedBucketIndex: Int? = null,
    val expandedCluster: ClusteredLabel? = null,
    val showRegionExitBanner: Boolean = false,
    val regionExitGraceSecondsRemaining: Int = 0,
    val showGraceExpiredDialog: Boolean = false,
    val showNoVisibleEntitiesHint: Boolean = false,
    val cameraBaseFov: CameraFieldOfView = CameraFieldOfView.fallback(),
    val cameraFov: CameraFieldOfView = CameraFieldOfView.fallback(),
    val zoomRatio: Float = 1f,
    val minZoomRatio: Float = 1f,
    val maxZoomRatio: Float = 1f,
    val screenWidthPx: Float = 0f,
    val screenHeightPx: Float = 0f,
    val debugRawAzimuthDeg: Float? = null,
    val debugSmoothedAzimuthDeg: Float? = null,
    val useRawSensorOverlay: Boolean = false,
    val visibleEntityCount: Int = 0,
    // Debug-only skyline diagnostics (populated on debug builds; see ExploreDebugReadout).
    val debugCameraElevationDeg: Float? = null,
    val horizonMeanAngleDeg: Float? = null,
    val horizonSegmentCount: Int = 0,
    val horizonEyeSource: String? = null,
    val horizonAzimuthOffsetDeg: Float = 0f,
    val horizonPitchOffsetDeg: Float = 0f,
    val showHorizonAlignmentWarning: Boolean = false,
    val exploreHere: ExploreHereUi = ExploreHereUi(),
    val virtualExploreSession: VirtualExploreSession? = null
) {
    val isVirtualExplore: Boolean get() = virtualExploreSession != null
}
