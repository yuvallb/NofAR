package com.nofar.core.model

data class Region(
    val id: Long,
    val name: String,
    val centerLat: Double,
    val centerLon: Double,
    val radiusMeters: Double
)

data class GeoEntity(
    val id: Long,
    val osmId: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double?,
    val placeTag: String?,
    val isPeak: Boolean
)

data class DemTile(val tileId: String, val southWestLat: Double, val southWestLon: Double, val rows: Int, val cols: Int)
