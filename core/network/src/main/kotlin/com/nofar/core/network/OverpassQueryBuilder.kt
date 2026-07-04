package com.nofar.core.network

import com.nofar.core.model.BoundingBox
import com.nofar.core.model.RegionBounds

object OverpassQueryBuilder {
    private const val PLACE_TAGS = "city|town|village|hamlet|isolated_dwelling|locality"
    private const val BBOX_PADDING_FACTOR = 1.02

    fun boundingBoxFromCircle(centerLat: Double, centerLon: Double, radiusM: Double): BoundingBox {
        val base = RegionBounds.boundingBox(centerLat, centerLon, radiusM)
        val latPad = (base.maxLat - base.minLat) * (BBOX_PADDING_FACTOR - 1.0) / 2.0
        val lonPad = (base.maxLon - base.minLon) * (BBOX_PADDING_FACTOR - 1.0) / 2.0
        return BoundingBox(
            minLat = base.minLat - latPad,
            maxLat = base.maxLat + latPad,
            minLon = base.minLon - lonPad,
            maxLon = base.maxLon + lonPad
        )
    }

    fun buildQuery(bbox: BoundingBox): String =
        """
        [out:json][timeout:120];
        (
          node["place"~"$PLACE_TAGS"](${bbox.south},${bbox.west},${bbox.north},${bbox.east});
          node["natural"="peak"](${bbox.south},${bbox.west},${bbox.north},${bbox.east});
          way["place"~"$PLACE_TAGS"](${bbox.south},${bbox.west},${bbox.north},${bbox.east});
          way["natural"="peak"](${bbox.south},${bbox.west},${bbox.north},${bbox.east});
          relation["place"~"$PLACE_TAGS"](${bbox.south},${bbox.west},${bbox.north},${bbox.east});
          relation["natural"="peak"](${bbox.south},${bbox.west},${bbox.north},${bbox.east});
        );
        out center;
        """.trimIndent()
}

private val BoundingBox.south: Double get() = minLat
private val BoundingBox.west: Double get() = minLon
private val BoundingBox.north: Double get() = maxLat
private val BoundingBox.east: Double get() = maxLon
