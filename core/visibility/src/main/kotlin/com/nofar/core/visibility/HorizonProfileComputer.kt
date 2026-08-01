package com.nofar.core.visibility

import com.nofar.core.model.AppConfig
import javax.inject.Inject
import kotlin.math.floor

/**
 * Full-360° terrain skyline profile for Explore horizon outline rendering.
 *
 * Runs on the low-frequency visibility pass cadence. Tune [AppConfig.HORIZON_AZIMUTH_STEP_DEG],
 * [AppConfig.HORIZON_RAY_STEP_M], and [AppConfig.HORIZON_MAX_RADIUS_M] if the combined pass exceeds
 * the Requirements §8 visibility budget on low-end devices.
 */
data class HorizonProfile(val azimuthStepDeg: Float, val elevationAnglesDeg: FloatArray) {
    fun azimuthDegForIndex(index: Int): Float = index * azimuthStepDeg

    /** Circular linear interpolation — avoids bucket-floor seams at 0°/360°. */
    fun sampleElevationDeg(azimuthDeg: Float): Float {
        if (elevationAnglesDeg.isEmpty()) return 0f
        val step = azimuthStepDeg
        val bucketCount = elevationAnglesDeg.size
        val normalized = HorizonProjector.normalizeAzimuthDeg(azimuthDeg)
        val indexFloat = normalized / step
        val lowerIndex = floor(indexFloat).toInt() % bucketCount
        val upperIndex = (lowerIndex + 1) % bucketCount
        val fraction = indexFloat - floor(indexFloat)
        val lower = elevationAnglesDeg[lowerIndex]
        val upper = elevationAnglesDeg[upperIndex]
        return lower * (1f - fraction) + upper * fraction
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HorizonProfile) return false
        return azimuthStepDeg == other.azimuthStepDeg &&
            elevationAnglesDeg.contentEquals(other.elevationAnglesDeg)
    }

    override fun hashCode(): Int {
        var result = azimuthStepDeg.hashCode()
        result = 31 * result + elevationAnglesDeg.contentHashCode()
        return result
    }
}

class HorizonProfileComputer
@Inject
constructor() {
    private val rayMarcher = TerrainRayMarcher()

    /**
     * Sweeps a full-360° skyline profile.
     *
     * @param maxRadiusM outward reach of each azimuth ray. Passed from the caller so it can match the
     * Explore entity-collection radius (H-DEC-3), capped at [AppConfig.HORIZON_MAX_RADIUS_M] for budget.
     */
    fun sweep(
        observerLat: Double,
        observerLon: Double,
        observerEyeM: Double,
        sampler: DemSampler,
        maxRadiusM: Double = AppConfig.HORIZON_MAX_RADIUS_M
    ): HorizonProfile {
        val azimuthStepDeg = AppConfig.HORIZON_AZIMUTH_STEP_DEG
        val bucketCount = (360f / azimuthStepDeg).toInt()
        val elevationAnglesDeg = FloatArray(bucketCount)

        for (bucketIndex in 0 until bucketCount) {
            val azimuthDeg = bucketIndex * azimuthStepDeg
            elevationAnglesDeg[bucketIndex] =
                sweepAzimuth(
                    observerLat = observerLat,
                    observerLon = observerLon,
                    observerEyeM = observerEyeM,
                    azimuthDeg = azimuthDeg.toDouble(),
                    sampler = sampler,
                    maxRadiusM = maxRadiusM
                ).toFloat()
        }

        // Keep raw per-azimuth angles. A circular moving average was previously applied here but it
        // crushed narrow steep ridges (a single high bucket averaged with flat neighbors), so the
        // projector stopped breaking segments for terrain that should leave the vertical frustum
        // (H-DEC-1 Option A). Screen-side `sampleElevationDeg` already interpolates between buckets.
        return HorizonProfile(
            azimuthStepDeg = azimuthStepDeg,
            elevationAnglesDeg = elevationAnglesDeg
        )
    }

    private fun sweepAzimuth(
        observerLat: Double,
        observerLon: Double,
        observerEyeM: Double,
        azimuthDeg: Double,
        sampler: DemSampler,
        maxRadiusM: Double
    ): Double {
        val stepM = AppConfig.HORIZON_RAY_STEP_M
        val groundElevationM = sampler.elevationAt(observerLat, observerLon)?.toDouble()
        val elevations =
            if (groundElevationM != null) {
                collectRayElevations(
                    observerLat = observerLat,
                    observerLon = observerLon,
                    azimuthDeg = azimuthDeg,
                    groundElevationM = groundElevationM,
                    maxRadiusM = maxRadiusM,
                    stepM = stepM,
                    sampler = sampler
                )
            } else {
                emptyList()
            }
        return elevationAngleAtHorizon(
            observerEyeM = observerEyeM,
            groundElevationM = groundElevationM,
            elevations = elevations,
            stepM = stepM
        )
    }

    private fun collectRayElevations(
        observerLat: Double,
        observerLon: Double,
        azimuthDeg: Double,
        groundElevationM: Double,
        maxRadiusM: Double,
        stepM: Double,
        sampler: DemSampler
    ): List<Double> {
        val sampleCount = GeoMath.buildRaySampleCount(maxRadiusM, stepM)
        val elevations = ArrayList<Double>(sampleCount)
        elevations += groundElevationM
        for (index in 1 until sampleCount) {
            val distanceM = minOf(index * stepM, maxRadiusM)
            val (lat, lon) = GeoMath.destinationPoint(observerLat, observerLon, azimuthDeg, distanceM)
            // H-P1-07: stop the ray at the first missing sample rather than holding the last elevation.
            // Hold-last invents flat plateaus past tile holes and drew false skyline lobes at coverage
            // edges; breaking keeps the skyline honest to the DEM footprint.
            val sampledElevationM = sampler.elevationAt(lat, lon)?.toDouble() ?: break
            // H-P1-06: D is the ray length (maxRadiusM), matching TerrainRayMarcher's per-target path
            // length semantics. H-P1-10: gate on the same flag so flat-earth golden tests stay aligned.
            val bulge =
                if (TerrainRayMarcher.applyEarthCurvature) {
                    GeoMath.earthBulgeM(distanceM, maxRadiusM)
                } else {
                    0.0
                }
            elevations += sampledElevationM + bulge
        }
        return elevations
    }

    private fun elevationAngleAtHorizon(
        observerEyeM: Double,
        groundElevationM: Double?,
        elevations: List<Double>,
        stepM: Double
    ): Double {
        if (groundElevationM == null || elevations.size < 2) {
            return 0.0
        }
        // Single eye for both slope selection and the elevation angle: findHorizonIndex reconstructs
        // the eye as groundElevationM + eyeHeightM, so passing (observerEyeM - groundElevationM) makes
        // both use exactly observerEyeM. No coerceAtLeast — clamping the height here would let the eye
        // used for the angle diverge from the eye used to pick the horizon (the "line only when
        // pitched at the sky" bug when GPS sits below the DEM). See H-P0-01.
        val eyeHeightM = observerEyeM - groundElevationM
        val horizonIndex = rayMarcher.findHorizonIndex(elevations, stepM, eyeHeightM)
        val horizonDistanceM = horizonIndex * stepM
        return if (horizonDistanceM <= 0.0) {
            0.0
        } else {
            GeoMath.elevationAngleDeg(
                observerEyeM = observerEyeM,
                targetElevationM = elevations[horizonIndex],
                horizontalDistanceM = horizonDistanceM
            )
        }
    }
}
