package com.nofar.core.visibility

import com.nofar.core.model.AppConfig
import com.nofar.core.model.GeoEntity
import com.nofar.core.model.GeoEntityType
import com.nofar.core.model.RegionBounds

data class HereContext(val place: GeoEntity? = null, val peak: GeoEntity? = null) {
    val entityIds: Set<String> =
        buildSet {
            place?.id?.let { add(it) }
            peak?.id?.let { add(it) }
        }

    val isEmpty: Boolean
        get() = place == null && peak == null
}

fun List<VisibleEntity>.excludingHereContext(hereContext: HereContext): List<VisibleEntity> = if (hereContext.isEmpty) {
    this
} else {
    filterNot { visible -> visible.entity.id in hereContext.entityIds }
}

object HereContextResolver {
    fun resolve(observerLat: Double, observerLon: Double, entities: Collection<GeoEntity>): HereContext {
        val place =
            entities
                .asSequence()
                .filter { entity -> entity.type != GeoEntityType.PEAK }
                .filter { entity -> containsObserver(observerLat, observerLon, entity) }
                .minWithOrNull(placeComparator(observerLat, observerLon))

        val peak =
            entities
                .asSequence()
                .filter { entity -> entity.type == GeoEntityType.PEAK }
                .map { entity -> entity to distanceM(observerLat, observerLon, entity) }
                .filter { (_, distanceM) -> distanceM <= AppConfig.EXPLORE_HERE_PEAK_RADIUS_M }
                .minByOrNull { (_, distanceM) -> distanceM }
                ?.first

        return HereContext(place = place, peak = peak)
    }

    private fun containsObserver(observerLat: Double, observerLon: Double, entity: GeoEntity): Boolean {
        val radiusM = effectiveFootprintRadiusM(entity)
        return distanceM(observerLat, observerLon, entity) <= radiusM
    }

    private fun effectiveFootprintRadiusM(entity: GeoEntity): Double =
        entity.footprintRadiusM ?: AppConfig.FOOTPRINT_RADIUS_MIN_M

    private fun distanceM(observerLat: Double, observerLon: Double, entity: GeoEntity): Double =
        RegionBounds.haversineDistanceM(observerLat, observerLon, entity.lat, entity.lon)

    private fun placeComparator(observerLat: Double, observerLon: Double): Comparator<GeoEntity> = compareBy(
        { entity -> effectiveFootprintRadiusM(entity) },
        { entity -> placeSpecificityRank(entity.type) },
        { entity -> distanceM(observerLat, observerLon, entity) },
        { entity -> entity.name }
    )

    /** Lower rank = more local settlement type (preferred when footprints tie). */
    internal fun placeSpecificityRank(type: GeoEntityType): Int = when (type) {
        GeoEntityType.LOCALITY -> 0
        GeoEntityType.ISOLATED_DWELLING -> 1
        GeoEntityType.HAMLET -> 2
        GeoEntityType.VILLAGE -> 3
        GeoEntityType.TOWN -> 4
        GeoEntityType.CITY -> 5
        GeoEntityType.PEAK -> Int.MAX_VALUE
    }
}
