package com.nofar.core.visibility

import com.nofar.core.model.AppConfig
import com.nofar.core.model.CellMembership

internal object CoverageRayExtent {
    fun observerInsideCells(cellIds: Set<String>, observerLat: Double, observerLon: Double): Boolean =
        CellMembership.hasCell(cellIds, observerLat, observerLon)

    fun sampleInsideCells(cellIds: Set<String>, lat: Double, lon: Double): Boolean =
        CellMembership.hasCell(cellIds, lat, lon)

    fun maxHorizonRadiusM(): Double = AppConfig.EXPLORE_ENTITY_QUERY_RADIUS_M

    /** Fixed-radius horizon for map preview (no circular region boundary). */
    fun maxDistanceInsideAnyRegionM(
        cellIds: Set<String>,
        observerLat: Double,
        observerLon: Double,
        @Suppress("UNUSED_PARAMETER") bearingDeg: Double
    ): Double = if (observerInsideCells(cellIds, observerLat, observerLon)) maxHorizonRadiusM() else 0.0

    fun observerInsideAnyRegion(cellIds: Set<String>, observerLat: Double, observerLon: Double): Boolean =
        observerInsideCells(cellIds, observerLat, observerLon)

    fun sampleInsideAnyRegion(cellIds: Set<String>, lat: Double, lon: Double): Boolean =
        sampleInsideCells(cellIds, lat, lon)
}
