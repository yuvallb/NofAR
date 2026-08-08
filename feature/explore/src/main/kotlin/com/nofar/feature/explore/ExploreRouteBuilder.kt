package com.nofar.feature.explore

import java.util.UUID

const val EXPLORE_ROUTE = "explore"
const val EXPLORE_ROUTE_WITH_ARGS =
    "explore?regionId={regionId}&virtualLat={virtualLat}&virtualLon={virtualLon}"
const val EXPLORE_START_ROUTE = "explore?regionId=&virtualLat=&virtualLon="

data class VirtualExploreSession(val primaryRegionId: UUID, val observerLat: Double, val observerLon: Double)

object ExploreRouteBuilder {
    fun build(regionId: UUID?, virtualLat: Double? = null, virtualLon: Double? = null): String {
        val regionArg = regionId?.toString().orEmpty()
        val latArg = virtualLat?.toString().orEmpty()
        val lonArg = virtualLon?.toString().orEmpty()
        return "explore?regionId=$regionArg&virtualLat=$latArg&virtualLon=$lonArg"
    }

    fun parseVirtualSession(
        regionIdRaw: String?,
        virtualLatRaw: String?,
        virtualLonRaw: String?
    ): VirtualExploreSession? {
        val regionId =
            regionIdRaw?.takeIf { it.isNotBlank() }?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val lat = virtualLatRaw?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
        val lon = virtualLonRaw?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
        val coordinatesValid =
            lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0
        return if (regionId != null && coordinatesValid) {
            VirtualExploreSession(primaryRegionId = regionId, observerLat = lat, observerLon = lon)
        } else {
            null
        }
    }

    fun parseRegionId(regionIdRaw: String?): UUID? =
        regionIdRaw?.takeIf { it.isNotBlank() }?.let { runCatching { UUID.fromString(it) }.getOrNull() }
}
