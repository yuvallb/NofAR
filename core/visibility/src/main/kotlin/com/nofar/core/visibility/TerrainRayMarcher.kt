package com.nofar.core.visibility

/**
 * Terrain profile ray-march with earth-curvature correction.
 *
 * Ports slope-comparison / line-of-sight logic from [scripts/line_dem_profile.py] and applies
 * bulge correction per Requirements §3.3.3.
 */
@Suppress("ReturnCount")
class TerrainRayMarcher {
    private val sampleDistances = DoubleArray(MAX_SAMPLES)
    private val sampleLats = DoubleArray(MAX_SAMPLES)
    private val sampleLons = DoubleArray(MAX_SAMPLES)

    /**
     * Returns true when no terrain sample along the profile blocks the sight line to the target.
     *
     * Missing DEM samples along the ray (excluding endpoints) fail closed: the target is treated as
     * not visible rather than assuming clear line of sight through unknown terrain.
     *
     * Long rays (up to [AppConfig.EXPLORE_ENTITY_QUERY_RADIUS_M]) use near/far stepping so sample
     * count stays within [MAX_SAMPLES] without leaving an unsampled gap before the target.
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
        sampler: DemSampler
    ): Boolean {
        if (totalDistanceM <= 0.0) return true

        val distances = RayDistanceSteps.entityRayDistances(totalDistanceM, rayStepM)
        val sampleCount = distances.size.coerceAtMost(MAX_SAMPLES)
        if (sampleCount < 2) return true

        val bearing = GeoMath.initialBearingDeg(observerLat, observerLon, targetLat, targetLon)
        for (index in 0 until sampleCount) {
            val distance = distances[index]
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

        return (1 until sampleCount - 1).none { index ->
            val distance = sampleDistances[index]
            val terrainElevation =
                sampler.elevationAt(sampleLats[index], sampleLons[index])?.toDouble()
                    // Fail closed: missing DEM counts as a blocker → none returns false → not visible.
                    ?: return@none true
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

    /**
     * Finds the horizon index using the max-slope algorithm.
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

    fun findHorizonIndexWithDistances(
        distanceElevationSamples: List<Pair<Double, Double>>,
        observerHeightM: Double
    ): Int {
        if (distanceElevationSamples.size < 2) return 0
        val groundElevation = distanceElevationSamples.first().second
        val observerEye = groundElevation + observerHeightM
        return distanceElevationSamples.indices
            .drop(1)
            .mapNotNull { index ->
                val (distanceM, elevationM) = distanceElevationSamples[index]
                if (distanceM <= 0.0) return@mapNotNull null
                index to ((elevationM - observerEye) / distanceM)
            }.maxByOrNull { it.second }
            ?.first ?: 0
    }

    companion object {
        private const val MAX_SAMPLES = 512
        private const val OCCLUSION_TOLERANCE_M = 1.0

        /** When true, ray-march applies earth bulge correction. Disable for flat-earth tests. */
        var applyEarthCurvature: Boolean = true
    }
}
