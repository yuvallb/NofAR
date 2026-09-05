package com.nofar.core.data.prepare

import com.nofar.core.model.DemTileId
import com.nofar.core.model.GeoEntity
import com.nofar.core.model.GeoMathBounds

object RegionNamePolicy {
    private val AUTO_NAME_REGEX = Regex("""(Region|Area|Downloaded maps) near -?\d+\.\d+, -?\d+\.\d+""")

    fun isUserProvidedName(name: String): Boolean {
        val trimmed = name.trim()
        return trimmed.isNotBlank() &&
            trimmed != "Downloaded maps" &&
            !AUTO_NAME_REGEX.matches(trimmed)
    }

    fun formatAutoName(centerLat: Double, centerLon: Double): String =
        "Area near ${"%.2f".format(centerLat)}, ${"%.2f".format(centerLon)}"
}

object CoverageNameResolver {
    private val PLACE_TYPES =
        setOf(
            com.nofar.core.model.GeoEntityType.CITY,
            com.nofar.core.model.GeoEntityType.TOWN,
            com.nofar.core.model.GeoEntityType.VILLAGE
        )

    fun referenceCenterFromCellIds(cellIds: List<String>): Pair<Double, Double> {
        val coords = cellIds.mapNotNull { DemTileId.parse(it) }
        if (coords.isEmpty()) return 0.0 to 0.0
        val lat = coords.map { it.first + 0.5 }.average()
        val lon = coords.map { it.second + 0.5 }.average()
        return lat to lon
    }

    /**
     * Nearest city/town/village name, or `"Downloaded maps"` when none exist
     * (plan T4 auto-name fallback).
     */
    fun closestEntityName(entities: List<GeoEntity>, referenceLat: Double, referenceLon: Double): String {
        val places = entities.filter { it.type in PLACE_TYPES && it.name.isNotBlank() }
        if (places.isEmpty()) return "Downloaded maps"
        return places
            .minByOrNull { entity ->
                GeoMathBounds.haversineDistanceM(referenceLat, referenceLon, entity.lat, entity.lon)
            }?.name ?: "Downloaded maps"
    }
}
