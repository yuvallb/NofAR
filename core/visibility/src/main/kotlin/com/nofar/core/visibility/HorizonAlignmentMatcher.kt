package com.nofar.core.visibility

import com.nofar.core.model.AppConfig
import kotlin.math.abs
import kotlin.math.max

/**
 * Grid-searches azimuth/pitch offsets so a cached [HorizonProfile] skyline best matches a camera profile.
 */
object HorizonAlignmentMatcher {
    fun match(
        cameraProfileYNormalized: FloatArray,
        horizonProfile: HorizonProfile,
        trueAzimuthDeg: Float,
        cameraElevationDeg: Float,
        fov: CameraFieldOfView,
        screenWidthPx: Float,
        screenHeightPx: Float
    ): HorizonAlignmentResult {
        if (!hasValidInputs(cameraProfileYNormalized, horizonProfile, screenWidthPx, screenHeightPx)) {
            return reject(HorizonAlignmentRejectReason.INSUFFICIENT_SAMPLES)
        }

        val zeroOffsetProfile =
            sampleDemProfileYNormalized(
                horizonProfile = horizonProfile,
                trueAzimuthDeg = trueAzimuthDeg,
                cameraElevationDeg = cameraElevationDeg,
                azimuthOffsetDeg = 0f,
                pitchOffsetDeg = 0f,
                fov = fov,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                columnCount = cameraProfileYNormalized.size
            )
        return when {
            HorizonSkylineExtractor.normalizedVariance(zeroOffsetProfile) <
                AppConfig.HORIZON_ALIGN_MIN_DEM_Y_VARIANCE ->
                reject(HorizonAlignmentRejectReason.FLAT_SKYLINE)
            else ->
                evaluateBestMatch(
                    cameraProfileYNormalized = cameraProfileYNormalized,
                    horizonProfile = horizonProfile,
                    trueAzimuthDeg = trueAzimuthDeg,
                    cameraElevationDeg = cameraElevationDeg,
                    fov = fov,
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx,
                    zeroOffsetProfile = zeroOffsetProfile
                )
        }
    }

    internal fun sampleDemProfileYNormalized(
        horizonProfile: HorizonProfile,
        trueAzimuthDeg: Float,
        cameraElevationDeg: Float,
        azimuthOffsetDeg: Float,
        pitchOffsetDeg: Float,
        fov: CameraFieldOfView,
        screenWidthPx: Float,
        screenHeightPx: Float,
        columnCount: Int
    ): FloatArray {
        val orientedFov = fov.orientedForScreen(screenWidthPx, screenHeightPx)
        val halfHorizontalFov = orientedFov.horizontalDeg / 2f
        val halfVerticalFov = orientedFov.verticalDeg / 2f
        if (halfHorizontalFov <= 0f || halfVerticalFov <= 0f || columnCount <= 0) {
            return FloatArray(columnCount) { Float.NaN }
        }

        val adjustedAzimuth = trueAzimuthDeg + azimuthOffsetDeg
        val adjustedPitch = cameraElevationDeg + pitchOffsetDeg
        val profile = FloatArray(columnCount)
        for (columnIndex in 0 until columnCount) {
            val fraction =
                if (columnIndex == 0 && columnCount == 1) {
                    0f
                } else {
                    columnIndex.toFloat() / (columnCount - 1)
                }
            val headingDelta = -halfHorizontalFov + fraction * (2f * halfHorizontalFov)
            val bearingDeg = HorizonProjector.normalizeAzimuthDeg(adjustedAzimuth + headingDelta)
            val elevationAngleDeg = horizonProfile.sampleElevationDeg(bearingDeg).toDouble()
            val relativeElevation = elevationAngleDeg - adjustedPitch.toDouble()
            profile[columnIndex] =
                if (abs(relativeElevation) > halfVerticalFov) {
                    Float.NaN
                } else {
                    val anchorY =
                        ScreenProjector.anchorYPx(
                            relativeElevationDeg = relativeElevation,
                            halfVerticalFovDeg = halfVerticalFov,
                            screenHeightPx = screenHeightPx
                        )
                    anchorY / screenHeightPx
                }
        }
        return profile
    }

    private fun hasValidInputs(
        cameraProfileYNormalized: FloatArray,
        horizonProfile: HorizonProfile,
        screenWidthPx: Float,
        screenHeightPx: Float
    ): Boolean = cameraProfileYNormalized.isNotEmpty() &&
        screenWidthPx > 0f &&
        screenHeightPx > 0f &&
        horizonProfile.elevationAnglesDeg.isNotEmpty()

    private fun searchBestOffset(
        cameraProfileYNormalized: FloatArray,
        horizonProfile: HorizonProfile,
        trueAzimuthDeg: Float,
        cameraElevationDeg: Float,
        fov: CameraFieldOfView,
        screenWidthPx: Float,
        screenHeightPx: Float
    ): OffsetSearchResult {
        var bestAzimuth = 0f
        var bestPitch = 0f
        var bestCost = Float.POSITIVE_INFINITY
        val azimuthStep = AppConfig.HORIZON_ALIGN_SEARCH_STEP_DEG
        val pitchStep = AppConfig.HORIZON_ALIGN_SEARCH_STEP_DEG
        var azimuthOffset = -AppConfig.HORIZON_ALIGN_AZIMUTH_SEARCH_DEG
        while (azimuthOffset <= AppConfig.HORIZON_ALIGN_AZIMUTH_SEARCH_DEG + azimuthStep * 0.01f) {
            var pitchOffset = -AppConfig.HORIZON_ALIGN_PITCH_SEARCH_DEG
            while (pitchOffset <= AppConfig.HORIZON_ALIGN_PITCH_SEARCH_DEG + pitchStep * 0.01f) {
                val demProfile =
                    sampleDemProfileYNormalized(
                        horizonProfile = horizonProfile,
                        trueAzimuthDeg = trueAzimuthDeg,
                        cameraElevationDeg = cameraElevationDeg,
                        azimuthOffsetDeg = azimuthOffset,
                        pitchOffsetDeg = pitchOffset,
                        fov = fov,
                        screenWidthPx = screenWidthPx,
                        screenHeightPx = screenHeightPx,
                        columnCount = cameraProfileYNormalized.size
                    )
                val cost = meanAbsError(cameraProfileYNormalized, demProfile)
                if (cost != null && cost < bestCost) {
                    bestCost = cost
                    bestAzimuth = azimuthOffset
                    bestPitch = pitchOffset
                }
                pitchOffset += pitchStep
            }
            azimuthOffset += azimuthStep
        }
        return OffsetSearchResult(bestAzimuth, bestPitch, bestCost)
    }

    private fun evaluateBestMatch(
        cameraProfileYNormalized: FloatArray,
        horizonProfile: HorizonProfile,
        trueAzimuthDeg: Float,
        cameraElevationDeg: Float,
        fov: CameraFieldOfView,
        screenWidthPx: Float,
        screenHeightPx: Float,
        zeroOffsetProfile: FloatArray
    ): HorizonAlignmentResult {
        val bestMatch =
            searchBestOffset(
                cameraProfileYNormalized = cameraProfileYNormalized,
                horizonProfile = horizonProfile,
                trueAzimuthDeg = trueAzimuthDeg,
                cameraElevationDeg = cameraElevationDeg,
                fov = fov,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx
            )
        if (!bestMatch.meanAbsError.isFinite()) {
            return reject(HorizonAlignmentRejectReason.INSUFFICIENT_SAMPLES)
        }

        val baselineCost =
            meanAbsError(cameraProfileYNormalized, zeroOffsetProfile) ?: Float.POSITIVE_INFINITY
        val improvedEnough =
            baselineCost.isFinite() &&
                bestMatch.meanAbsError <= baselineCost * (1f - AppConfig.HORIZON_ALIGN_MIN_IMPROVEMENT_FRACTION)
        val maxOffset = max(abs(bestMatch.azimuthOffsetDeg), abs(bestMatch.pitchOffsetDeg))
        return when {
            !improvedEnough ->
                HorizonAlignmentResult(
                    azimuthOffsetDeg = bestMatch.azimuthOffsetDeg,
                    pitchOffsetDeg = bestMatch.pitchOffsetDeg,
                    meanAbsError = bestMatch.meanAbsError,
                    accepted = false,
                    rejectReason = HorizonAlignmentRejectReason.LOW_CONFIDENCE
                )
            maxOffset > AppConfig.HORIZON_ALIGN_MAX_AUTO_DEG ->
                HorizonAlignmentResult(
                    azimuthOffsetDeg = bestMatch.azimuthOffsetDeg,
                    pitchOffsetDeg = bestMatch.pitchOffsetDeg,
                    meanAbsError = bestMatch.meanAbsError,
                    accepted = false,
                    rejectReason = HorizonAlignmentRejectReason.OVER_THRESHOLD
                )
            else ->
                HorizonAlignmentResult(
                    azimuthOffsetDeg = bestMatch.azimuthOffsetDeg,
                    pitchOffsetDeg = bestMatch.pitchOffsetDeg,
                    meanAbsError = bestMatch.meanAbsError,
                    accepted = true,
                    rejectReason = null
                )
        }
    }

    private fun meanAbsError(camera: FloatArray, dem: FloatArray): Float? {
        if (camera.size != dem.size) return null
        var sum = 0f
        var count = 0
        for (index in camera.indices) {
            val cameraY = camera[index]
            val demY = dem[index]
            if (cameraY.isFinite() && demY.isFinite()) {
                sum += abs(cameraY - demY)
                count++
            }
        }
        return when {
            count == 0 -> null
            count.toFloat() / camera.size < AppConfig.HORIZON_ALIGN_MIN_VALID_COLUMN_FRACTION -> null
            else -> sum / count
        }
    }

    private fun reject(reason: HorizonAlignmentRejectReason): HorizonAlignmentResult = HorizonAlignmentResult(
        azimuthOffsetDeg = 0f,
        pitchOffsetDeg = 0f,
        meanAbsError = Float.POSITIVE_INFINITY,
        accepted = false,
        rejectReason = reason
    )

    private data class OffsetSearchResult(
        val azimuthOffsetDeg: Float,
        val pitchOffsetDeg: Float,
        val meanAbsError: Float
    )
}

object HorizonAlignmentGates {
    fun isNearHorizon(cameraElevationDeg: Float): Boolean =
        abs(cameraElevationDeg) <= AppConfig.HORIZON_ALIGN_MAX_CAMERA_ELEVATION_DEG

    fun isOrientationStable(
        anchor: com.nofar.core.model.DeviceOrientation,
        current: com.nofar.core.model.DeviceOrientation
    ): Boolean {
        val azimuthDelta = angularDeltaDeg(anchor.trueAzimuthDeg, current.trueAzimuthDeg)
        val pitchDelta = abs(current.cameraElevationDeg - anchor.cameraElevationDeg)
        val rollDelta = abs(current.rollDeg - anchor.rollDeg)
        return azimuthDelta <= AppConfig.HORIZON_ALIGN_STILL_AZIMUTH_DEG &&
            pitchDelta <= AppConfig.HORIZON_ALIGN_STILL_PITCH_DEG &&
            rollDelta <= AppConfig.HORIZON_ALIGN_STILL_ROLL_DEG
    }

    private fun angularDeltaDeg(fromDeg: Float, toDeg: Float): Float {
        var delta = toDeg - fromDeg
        while (delta > 180f) delta -= 360f
        while (delta < -180f) delta += 360f
        return abs(delta)
    }
}
