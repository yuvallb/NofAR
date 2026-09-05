package com.nofar.core.visibility

import com.nofar.core.model.AppConfig
import com.nofar.core.model.Region
import com.nofar.core.model.RegionBounds
import kotlin.math.min

internal object RegionRayExtent {
    private const val BINARY_SEARCH_ITERATIONS = 16
    private const val OUTSIDE_MARGIN_M = 100.0

    fun maxDistanceInsideRegionM(region: Region, observerLat: Double, observerLon: Double, bearingDeg: Double): Double {
        if (!RegionBounds.containsPoint(region, observerLat, observerLon)) return 0.0
        var insideM = 0.0
        var outsideM = region.radiusM * 2.0 + OUTSIDE_MARGIN_M
        repeat(BINARY_SEARCH_ITERATIONS) {
            val probeM = (insideM + outsideM) / 2.0
            val (lat, lon) = GeoMath.destinationPoint(observerLat, observerLon, bearingDeg, probeM)
            if (RegionBounds.containsPoint(region, lat, lon)) {
                insideM = probeM
            } else {
                outsideM = probeM
            }
        }
        return insideM
    }

    fun observerInsideAnyRegion(regions: List<Region>, observerLat: Double, observerLon: Double): Boolean =
        regions.any { RegionBounds.containsPoint(it, observerLat, observerLon) }

    fun sampleInsideAnyRegion(regions: List<Region>, lat: Double, lon: Double): Boolean =
        regions.any { RegionBounds.containsPoint(it, lat, lon) }

    fun maxDistanceInsideAnyRegionM(
        regions: List<Region>,
        observerLat: Double,
        observerLon: Double,
        bearingDeg: Double,
        maxCapM: Double = AppConfig.CONTRIBUTING_REGION_MAX_DISTANCE_M
    ): Double {
        if (regions.isEmpty() || !observerInsideAnyRegion(regions, observerLat, observerLon)) {
            return 0.0
        }
        val farthestInsideM =
            regions.maxOf { region ->
                maxDistanceInsideRegionM(
                    region = region,
                    observerLat = observerLat,
                    observerLon = observerLon,
                    bearingDeg = bearingDeg
                )
            }
        return min(maxCapM, farthestInsideM)
    }
}
