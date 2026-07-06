package com.nofar.core.model

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

data class BoundingBox(val minLat: Double, val maxLat: Double, val minLon: Double, val maxLon: Double)

object RegionBounds {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun boundingBox(centerLat: Double, centerLon: Double, radiusM: Double): BoundingBox {
        val deltaLat = radiusM / 111_320.0
        val cosLat = cos(Math.toRadians(centerLat)).coerceAtLeast(1e-6)
        val deltaLon = radiusM / (111_320.0 * cosLat)
        return BoundingBox(
            minLat = centerLat - deltaLat,
            maxLat = centerLat + deltaLat,
            minLon = centerLon - deltaLon,
            maxLon = centerLon + deltaLon
        )
    }

    fun haversineDistanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a =
            kotlin.math.sin(dLat / 2).pow(2) +
                kotlin.math.cos(Math.toRadians(lat1)) *
                kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * kotlin.math.asin(sqrt(a))
    }

    fun containsPoint(region: Region, lat: Double, lon: Double): Boolean =
        haversineDistanceM(region.centerLat, region.centerLon, lat, lon) <= region.radiusM
}
