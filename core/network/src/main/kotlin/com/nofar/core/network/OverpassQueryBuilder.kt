package com.nofar.core.network

import com.nofar.core.model.BoundingBox
import com.nofar.core.model.GeoMathBounds

object OverpassQueryBuilder {
    private const val PLACE_TAGS = "city|town|village"
    private const val QUERY_TIMEOUT_SECONDS = 180

    fun boundingBoxForCell(tileLat: Int, tileLon: Int): BoundingBox = BoundingBox(
        minLat = tileLat.toDouble(),
        maxLat = tileLat + 1.0,
        minLon = tileLon.toDouble(),
        maxLon = tileLon + 1.0
    )

    /** @deprecated Use [boundingBoxForCell]; retained for tests during migration. */
    fun boundingBoxFromCircle(centerLat: Double, centerLon: Double, radiusM: Double): BoundingBox =
        GeoMathBounds.boundingBox(centerLat, centerLon, radiusM)

    fun buildQuery(bbox: BoundingBox): String =
        """
        [out:json][timeout:$QUERY_TIMEOUT_SECONDS];
        (
          node["place"~"$PLACE_TAGS"](${bbox.south},${bbox.west},${bbox.north},${bbox.east});
          way["place"~"$PLACE_TAGS"](${bbox.south},${bbox.west},${bbox.north},${bbox.east});
          relation["place"~"$PLACE_TAGS"](${bbox.south},${bbox.west},${bbox.north},${bbox.east});
        )->.places;
        (
          node["natural"="peak"](${bbox.south},${bbox.west},${bbox.north},${bbox.east});
          way["natural"="peak"](${bbox.south},${bbox.west},${bbox.north},${bbox.east});
          relation["natural"="peak"](${bbox.south},${bbox.west},${bbox.north},${bbox.east});
        )->.peaks;
        .places out center;
        .peaks out center;
        """.trimIndent()

    fun cellsToBoundingBoxes(cells: List<Pair<Int, Int>>): List<BoundingBox> =
        cells.map { (lat, lon) -> boundingBoxForCell(lat, lon) }
}

private val BoundingBox.south: Double get() = minLat
private val BoundingBox.west: Double get() = minLon
private val BoundingBox.north: Double get() = maxLat
private val BoundingBox.east: Double get() = maxLon
