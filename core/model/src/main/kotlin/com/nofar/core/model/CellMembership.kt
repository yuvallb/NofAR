package com.nofar.core.model

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

data class BoundingBox(val minLat: Double, val maxLat: Double, val minLon: Double, val maxLon: Double)

/** Geometry helpers retained after circular membership was removed. */
object GeoMathBounds {
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
}

/** 1° cell membership for Explore and download coverage. */
object CellMembership {
    fun cellIdForPoint(lat: Double, lon: Double): String {
        val (tileLat, tileLon) = DemTileId.cellForPoint(lat, lon)
        return DemTileId.fromCoordinates(tileLat, tileLon)
    }

    fun hasCell(cellIds: Set<String>, lat: Double, lon: Double): Boolean = cellIdForPoint(lat, lon) in cellIds

    fun localDownloadCells(lat: Double, lon: Double): List<Pair<Int, Int>> = DemTileId.localCellRing(lat, lon)

    fun localDownloadCellIds(lat: Double, lon: Double): List<String> =
        localDownloadCells(lat, lon).map { (tileLat, tileLon) -> DemTileId.fromCoordinates(tileLat, tileLon) }
}

/** Default footprint radii (meters) when admin boundaries are not fetched. */
object FootprintDefaults {
    fun radiusM(type: GeoEntityType): Double? = when (type) {
        GeoEntityType.CITY -> 5_000.0
        GeoEntityType.TOWN -> 1_500.0
        GeoEntityType.VILLAGE -> 500.0
        GeoEntityType.PEAK -> null
        else -> null
    }
}
