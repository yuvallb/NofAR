package com.nofar.core.data.osm

import com.nofar.core.model.AppConfig
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Approximates a place's ground extent as a single radius (meters) from boundary geometry.
 * Geometry is treated as an unordered point cloud — no polygon ring stitching.
 */
object PlaceFootprintCalculator {
    fun computeRadiusM(points: List<Pair<Double, Double>>): Double? {
        if (points.isEmpty()) return null

        val sampled = samplePoints(points, AppConfig.FOOTPRINT_BOUNDARY_MAX_POINTS)
        val refLat = sampled.map { it.first }.average()
        val refLon = sampled.map { it.second }.average()
        val cosLat = cos(Math.toRadians(refLat)).coerceAtLeast(1e-6)

        var sumSq = 0.0
        var maxDistSq = 0.0
        for ((lat, lon) in sampled) {
            val dx = Math.toRadians(lon - refLon) * AppConfig.EARTH_RADIUS_METERS * cosLat
            val dy = Math.toRadians(lat - refLat) * AppConfig.EARTH_RADIUS_METERS
            val distSq = dx * dx + dy * dy
            sumSq += distSq
            if (distSq > maxDistSq) maxDistSq = distSq
        }

        val rGyration = sqrt(sumSq / sampled.size)
        val rMax = sqrt(maxDistSq)
        val scaled = rGyration * AppConfig.FOOTPRINT_RADIUS_GYRATION_FACTOR
        return scaled
            .coerceAtMost(rMax)
            .coerceIn(AppConfig.FOOTPRINT_RADIUS_MIN_M, AppConfig.FOOTPRINT_RADIUS_MAX_M)
    }

    private fun samplePoints(points: List<Pair<Double, Double>>, maxPoints: Int): List<Pair<Double, Double>> {
        if (points.size <= maxPoints) return points
        val stride = points.size.toDouble() / maxPoints
        return buildList(maxPoints) {
            var index = 0.0
            repeat(maxPoints) {
                add(points[index.toInt().coerceIn(0, points.lastIndex)])
                index += stride
            }
        }
    }
}
