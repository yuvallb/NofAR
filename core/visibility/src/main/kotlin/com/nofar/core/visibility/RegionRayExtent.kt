package com.nofar.core.visibility

import com.nofar.core.model.Region
import com.nofar.core.model.RegionBounds

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
}
