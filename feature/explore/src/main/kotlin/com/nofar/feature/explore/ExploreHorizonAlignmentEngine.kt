package com.nofar.feature.explore

import com.nofar.core.data.preferences.UserPreferencesRepository
import com.nofar.core.model.DeviceOrientation
import com.nofar.core.visibility.CameraFieldOfView
import com.nofar.core.visibility.FillCenterCrop
import com.nofar.core.visibility.HorizonAlignmentMatcher
import com.nofar.core.visibility.HorizonAlignmentRejectReason
import com.nofar.core.visibility.HorizonAlignmentResult
import com.nofar.core.visibility.HorizonProfile
import com.nofar.core.visibility.HorizonSkylineExtractor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExploreHorizonAlignmentEngine
@Inject
constructor(
    private val frameStore: ExploreCameraFrameStore,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend fun attemptAlignment(
        orientation: DeviceOrientation,
        horizonProfile: HorizonProfile,
        fov: CameraFieldOfView,
        screenWidthPx: Float,
        screenHeightPx: Float
    ): ExploreHorizonAlignmentOutcome {
        val cropped =
            frameStore.snapshot()?.let { frame ->
                val viewAspect = screenWidthPx / screenHeightPx
                FillCenterCrop.cropYPlane(frame.yPlane, frame.width, frame.height, viewAspect)
            }
        if (cropped == null) {
            return noCameraFrameOutcome()
        }

        val cameraProfile =
            HorizonSkylineExtractor.extractNormalizedProfile(
                yPlane = cropped.yPlane,
                width = cropped.width,
                height = cropped.height
            )
        val matchResult =
            HorizonAlignmentMatcher.match(
                cameraProfileYNormalized = cameraProfile,
                horizonProfile = horizonProfile,
                trueAzimuthDeg = orientation.trueAzimuthDeg,
                cameraElevationDeg = orientation.cameraElevationDeg,
                fov = fov,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx
            )

        if (matchResult.accepted) {
            userPreferencesRepository.setHorizonAlignmentOffsets(
                azimuthOffsetDeg = matchResult.azimuthOffsetDeg,
                pitchOffsetDeg = matchResult.pitchOffsetDeg
            )
        }
        return ExploreHorizonAlignmentOutcome(
            result = matchResult,
            offsetsApplied = matchResult.accepted
        )
    }

    private fun noCameraFrameOutcome(): ExploreHorizonAlignmentOutcome = ExploreHorizonAlignmentOutcome(
        result =
        HorizonAlignmentResult(
            azimuthOffsetDeg = 0f,
            pitchOffsetDeg = 0f,
            meanAbsError = Float.POSITIVE_INFINITY,
            accepted = false,
            rejectReason = HorizonAlignmentRejectReason.NO_CAMERA_FRAME
        ),
        offsetsApplied = false
    )
}

data class ExploreHorizonAlignmentOutcome(val result: HorizonAlignmentResult, val offsetsApplied: Boolean)
