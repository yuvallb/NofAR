package com.nofar.core.visibility

/**
 * Terrain profile ray-march with earth-curvature correction.
 *
 * Ports slope-comparison / line-of-sight logic from [scripts/line_dem_profile.py] and applies
 * bulge correction per Requirements §3.3.3.
 */
class TerrainRayMarcher {
    private val sampleDistances = DoubleArray(MAX_SAMPLES)
    private val sampleLats = DoubleArray(MAX_SAMPLES)
    private val sampleLons = DoubleArray(MAX_SAMPLES)

    /**
     * Returns true when no terrain sample along the profile blocks the sight line to the target.
     */
    fun isTargetVisible(
        observerLat: Double,
        observerLon: Double,
        targetLat: Double,
        targetLon: Double,
        totalDistanceM: Double,
        observerEyeM: Double,
        targetElevationM: Double,
        rayStepM: Double,
        sampler: DemElevationSampler
    ): Boolean {
        if (totalDistanceM <= 0.0) return true

        val sampleCount = GeoMath.buildRaySampleCount(totalDistanceM, rayStepM).coerceAtMost(MAX_SAMPLES)
        val bearing = GeoMath.initialBearingDeg(observerLat, observerLon, targetLat, targetLon)
        fillSamplePoints(
            observerLat = observerLat,
            observerLon = observerLon,
            targetLat = targetLat,
            targetLon = targetLon,
            totalDistanceM = totalDistanceM,
            rayStepM = rayStepM,
            bearing = bearing,
            sampleCount = sampleCount
        )

        return (1 until sampleCount - 1).none { index ->
            val distance = sampleDistances[index]
            val terrainElevation =
                sampler.elevationAt(sampleLats[index], sampleLons[index])?.toDouble() ?: return@none false
            val sightLineHeight =
                observerEyeM + (targetElevationM - observerEyeM) * (distance / totalDistanceM)
            val bulge =
                if (applyEarthCurvature) {
                    GeoMath.earthBulgeM(distance, totalDistanceM)
                } else {
                    0.0
                }
            terrainElevation + bulge > sightLineHeight + OCCLUSION_TOLERANCE_M
        }
    }

    private fun fillSamplePoints(
        observerLat: Double,
        observerLon: Double,
        targetLat: Double,
        targetLon: Double,
        totalDistanceM: Double,
        rayStepM: Double,
        bearing: Double,
        sampleCount: Int
    ) {
        for (index in 0 until sampleCount) {
            val distance = minOf(index * rayStepM, totalDistanceM)
            sampleDistances[index] = distance
            if (index == sampleCount - 1) {
                sampleLats[index] = targetLat
                sampleLons[index] = targetLon
            } else {
                val (lat, lon) = GeoMath.destinationPoint(observerLat, observerLon, bearing, distance)
                sampleLats[index] = lat
                sampleLons[index] = lon
            }
        }
    }

    /**
     * Finds the horizon index using the Python [find_horizon] max-slope algorithm.
     * Exposed for golden-file parity tests.
     */
    fun findHorizonIndex(elevations: List<Double?>, fixedDistanceM: Double, observerHeightM: Double): Int {
        if (elevations.size < 2) return 0

        val groundElevation = elevations.firstOrNull() ?: 0.0
        val observerEye = groundElevation + observerHeightM
        return elevations.indices
            .drop(1)
            .mapNotNull { index ->
                val elevation = elevations[index] ?: return@mapNotNull null
                val distance = index * fixedDistanceM
                index to ((elevation - observerEye) / distance)
            }.maxByOrNull { it.second }
            ?.first ?: 0
    }

    companion object {
        private const val MAX_SAMPLES = 256
        private const val OCCLUSION_TOLERANCE_M = 0.5

        /** When true, ray-march applies earth bulge correction. Disable for flat-earth tests. */
        var applyEarthCurvature: Boolean = true
    }
}
