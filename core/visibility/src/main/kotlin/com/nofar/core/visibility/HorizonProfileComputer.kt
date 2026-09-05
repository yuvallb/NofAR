package com.nofar.core.visibility

import com.nofar.core.model.AppConfig
import javax.inject.Inject
import kotlin.math.floor

/**
 * Full-360° terrain skyline profile for Explore horizon outline rendering.
 *
 * Runs on the low-frequency visibility pass cadence. Tune [AppConfig.HORIZON_AZIMUTH_STEP_DEG] and
 * [AppConfig.HORIZON_RAY_STEP_M] if the combined pass exceeds the Requirements §8 visibility budget
 * on low-end devices.
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
     * @param maxRadiusM outward reach of each azimuth ray. Passed from the caller so it matches the
     * Explore entity-collection radius: [AppConfig.EXPLORE_ENTITY_QUERY_RADIUS_M].
     */
    fun sweep(
        observerLat: Double,
        observerLon: Double,
        observerEyeM: Double,
        sampler: DemSampler,
        maxRadiusM: Double = AppConfig.EXPLORE_ENTITY_QUERY_RADIUS_M
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
        val groundElevationM = sampler.elevationAt(observerLat, observerLon)?.toDouble()
        val samples =
            if (groundElevationM != null) {
                collectRaySamples(
                    observerLat = observerLat,
                    observerLon = observerLon,
                    azimuthDeg = azimuthDeg,
                    groundElevationM = groundElevationM,
                    maxRadiusM = maxRadiusM,
                    sampler = sampler
                )
            } else {
                emptyList()
            }
        return elevationAngleAtHorizon(
            observerEyeM = observerEyeM,
            groundElevationM = groundElevationM,
            samples = samples
        )
    }

    private fun collectRaySamples(
        observerLat: Double,
        observerLon: Double,
        azimuthDeg: Double,
        groundElevationM: Double,
        maxRadiusM: Double,
        sampler: DemSampler
    ): List<Pair<Double, Double>> {
        val distances = RayDistanceSteps.horizonDistances(maxRadiusM)
        val samples = ArrayList<Pair<Double, Double>>(distances.size)
        samples += 0.0 to groundElevationM
        for (index in 1 until distances.size) {
            val distanceM = distances[index]
            val (lat, lon) = GeoMath.destinationPoint(observerLat, observerLon, azimuthDeg, distanceM)
            val sampledElevationM = sampler.elevationAt(lat, lon)?.toDouble() ?: break
            val bulge =
                if (TerrainRayMarcher.applyEarthCurvature) {
                    GeoMath.earthBulgeM(distanceM, maxRadiusM)
                } else {
                    0.0
                }
            samples += distanceM to (sampledElevationM + bulge)
        }
        return samples
    }

    private fun elevationAngleAtHorizon(
        observerEyeM: Double,
        groundElevationM: Double?,
        samples: List<Pair<Double, Double>>
    ): Double {
        if (groundElevationM == null || samples.size < 2) {
            return Double.NaN
        }
        val eyeHeightM = observerEyeM - groundElevationM
        val horizonIndex = rayMarcher.findHorizonIndexWithDistances(samples, eyeHeightM)
        val horizonDistanceM = samples[horizonIndex].first
        return if (horizonDistanceM <= 0.0) {
            0.0
        } else {
            GeoMath.elevationAngleDeg(
                observerEyeM = observerEyeM,
                targetElevationM = samples[horizonIndex].second,
                horizontalDistanceM = horizonDistanceM
            )
        }
    }
}
