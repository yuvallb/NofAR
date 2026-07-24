package com.nofar.core.visibility

import com.nofar.core.model.AppConfig

/**
 * Full-360° terrain skyline profile for Explore horizon outline rendering.
 *
 * Runs on the low-frequency visibility pass cadence. Tune [AppConfig.HORIZON_AZIMUTH_STEP_DEG],
 * [AppConfig.HORIZON_RAY_STEP_M], and [AppConfig.HORIZON_MAX_RADIUS_M] if the combined pass exceeds
 * the Requirements §8 visibility budget on low-end devices.
 */
data class HorizonProfile(val azimuthStepDeg: Float, val elevationAnglesDeg: FloatArray) {
    fun azimuthDegForIndex(index: Int): Float = index * azimuthStepDeg

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

class HorizonProfileComputer {
    fun sweep(
        observerLat: Double,
        observerLon: Double,
        observerEyeM: Double,
        sampler: DemElevationSampler
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
                    sampler = sampler
                ).toFloat()
        }

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
        sampler: DemElevationSampler
    ): Double {
        val maxRadiusM = AppConfig.HORIZON_MAX_RADIUS_M
        val stepM = AppConfig.HORIZON_RAY_STEP_M
        val sampleCount = GeoMath.buildRaySampleCount(maxRadiusM, stepM)
        var maxAngleDeg = 0.0
        var sawDemSample = false

        for (index in 1 until sampleCount) {
            val distanceM = minOf(index * stepM, maxRadiusM)
            val (lat, lon) = GeoMath.destinationPoint(observerLat, observerLon, azimuthDeg, distanceM)
            val terrainElevationM = sampler.elevationAt(lat, lon)?.toDouble() ?: continue
            sawDemSample = true
            val bulge = GeoMath.earthBulgeM(distanceM, maxRadiusM)
            val angleDeg =
                GeoMath.elevationAngleDeg(
                    observerEyeM = observerEyeM,
                    targetElevationM = terrainElevationM + bulge,
                    horizontalDistanceM = distanceM
                )
            if (angleDeg > maxAngleDeg) {
                maxAngleDeg = angleDeg
            }
        }

        return if (sawDemSample) maxAngleDeg else 0.0
    }
}
