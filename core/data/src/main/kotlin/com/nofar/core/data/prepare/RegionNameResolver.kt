package com.nofar.core.data.prepare

import com.nofar.core.model.GeoEntity
import com.nofar.core.model.Region
import com.nofar.core.model.RegionBounds

object RegionNamePolicy {
    // Auto-generated names always end with US-formatted coordinates regardless of locale prefix.
    private val AUTO_NAME_REGEX = Regex("""^.+ -?\d+\.\d+, -?\d+\.\d+$""")

    fun isUserProvidedName(name: String): Boolean = name.isNotBlank() && !AUTO_NAME_REGEX.matches(name.trim())
}

object RegionNameResolver {
    fun closestEntityName(region: Region, entities: List<GeoEntity>): String? = entities
        .filter { it.name.isNotBlank() }
        .minByOrNull { entity ->
            RegionBounds.haversineDistanceM(
                region.centerLat,
                region.centerLon,
                entity.lat,
                entity.lon
            )
        }?.name
}
