package com.nofar.feature.explore

import java.util.UUID

const val EXPLORE_ROUTE = "explore"
const val EXPLORE_ROUTE_WITH_ARGS =
    "explore?coverageSetId={coverageSetId}&virtualLat={virtualLat}&virtualLon={virtualLon}"
const val EXPLORE_START_ROUTE = "explore?coverageSetId=&virtualLat=&virtualLon="

data class VirtualExploreSession(val primaryRegionId: UUID, val observerLat: Double, val observerLon: Double)

object ExploreRouteBuilder {
    fun build(coverageSetId: UUID?, virtualLat: Double? = null, virtualLon: Double? = null): String {
        val regionArg = coverageSetId?.toString().orEmpty()
        val latArg = virtualLat?.toString().orEmpty()
        val lonArg = virtualLon?.toString().orEmpty()
        return "explore?coverageSetId=$regionArg&virtualLat=$latArg&virtualLon=$lonArg"
    }

    fun parseVirtualSession(
        coverageSetIdRaw: String?,
        virtualLatRaw: String?,
        virtualLonRaw: String?
    ): VirtualExploreSession? {
        val coverageSetId =
            coverageSetIdRaw?.takeIf { it.isNotBlank() }?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val lat = virtualLatRaw?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
        val lon = virtualLonRaw?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
        val coordinatesValid =
            lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0
        return if (coverageSetId != null && coordinatesValid) {
            VirtualExploreSession(primaryRegionId = coverageSetId, observerLat = lat, observerLon = lon)
        } else {
            null
        }
    }

    fun parseRegionId(coverageSetIdRaw: String?): UUID? =
        coverageSetIdRaw?.takeIf { it.isNotBlank() }?.let { runCatching { UUID.fromString(it) }.getOrNull() }
}
