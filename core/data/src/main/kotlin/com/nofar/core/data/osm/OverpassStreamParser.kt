@file:Suppress("CyclomaticComplexMethod", "ReturnCount")

package com.nofar.core.data.osm

import com.nofar.core.model.ElevationSource
import com.nofar.core.model.GeoEntity
import com.nofar.core.model.GeoEntityType
import com.nofar.core.model.LabelLanguage
import com.nofar.core.model.OsmType
import com.squareup.moshi.JsonReader
import java.io.InputStream
import java.time.Instant
import okio.buffer
import okio.source

data class ParsedOsmElement(
    val osmType: OsmType,
    val osmId: Long,
    val name: String,
    val canonicalName: String,
    val type: GeoEntityType,
    val lat: Double,
    val lon: Double,
    val elevation: Double?
) {
    val entityId: String get() = "${osmType.name.lowercase()}/$osmId"
}

private sealed interface ParsedStreamItem {
    data class PlaceOrPeak(val element: ParsedOsmElement) : ParsedStreamItem

    data class Footprint(val entityId: String, val radiusM: Double) : ParsedStreamItem
}

private data class BoundaryMember(val osmType: OsmType, val ref: Long, val geometry: List<Pair<Double, Double>>)

/**
 * Streams Overpass JSON elements one-by-one without loading the full response into memory.
 */
class OverpassStreamParser {
    fun parse(input: InputStream, onElement: (ParsedOsmElement) -> Unit): Int =
        parse(input, LabelLanguage.DEFAULT, onElement)

    fun parse(
        input: InputStream,
        labelLanguage: LabelLanguage,
        onElement: (ParsedOsmElement) -> Unit,
        onFootprint: (entityId: String, radiusM: Double) -> Unit = { _, _ -> }
    ): Int {
        val reader = input.source().buffer().let { JsonReader.of(it) }
        val knownPlaceIds = mutableSetOf<String>()
        val footprintByEntityId = mutableMapOf<String, Double>()
        var count = 0
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "elements" ->
                    count +=
                        parseElementsArray(
                            reader = reader,
                            labelLanguage = labelLanguage,
                            knownPlaceIds = knownPlaceIds,
                            footprintByEntityId = footprintByEntityId,
                            onElement = onElement
                        )
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        footprintByEntityId.forEach { (entityId, radiusM) -> onFootprint(entityId, radiusM) }
        return count
    }

    private fun parseElementsArray(
        reader: JsonReader,
        labelLanguage: LabelLanguage,
        knownPlaceIds: MutableSet<String>,
        footprintByEntityId: MutableMap<String, Double>,
        onElement: (ParsedOsmElement) -> Unit
    ): Int {
        var count = 0
        reader.beginArray()
        while (reader.hasNext()) {
            parseStreamItems(reader, labelLanguage, knownPlaceIds).forEach { item ->
                when (item) {
                    is ParsedStreamItem.PlaceOrPeak -> {
                        onElement(item.element)
                        count++
                    }
                    is ParsedStreamItem.Footprint -> {
                        val existing = footprintByEntityId[item.entityId]
                        footprintByEntityId[item.entityId] =
                            if (existing == null) item.radiusM else minOf(existing, item.radiusM)
                    }
                }
            }
        }
        reader.endArray()
        return count
    }

    private fun parseStreamItems(
        reader: JsonReader,
        labelLanguage: LabelLanguage,
        knownPlaceIds: MutableSet<String>
    ): List<ParsedStreamItem> {
        var osmType: OsmType? = null
        var osmId = 0L
        var lat: Double? = null
        var lon: Double? = null
        var centerLat: Double? = null
        var centerLon: Double? = null
        val tags = mutableMapOf<String, String>()
        var members: List<BoundaryMember>? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "type" -> osmType = OsmType.fromTag(reader.nextString())
                "id" -> osmId = reader.nextLong()
                "lat" -> lat = reader.nextDouble()
                "lon" -> lon = reader.nextDouble()
                "center" -> parseCenter(reader)?.let { (cLat, cLon) ->
                    centerLat = cLat
                    centerLon = cLon
                }
                "tags" -> parseTags(reader, tags)
                "members" -> members = parseMembers(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val resolvedType = osmType ?: return emptyList()

        if (tags["boundary"].equals("administrative", ignoreCase = true) && members != null) {
            return parseAdministrativeBoundaryFootprints(members, knownPlaceIds)
        }

        val entityType = resolveEntityType(tags) ?: return emptyList()
        val displayName = OsmNameResolver.resolveDisplayName(tags, labelLanguage) ?: return emptyList()
        val canonicalName = OsmNameResolver.resolveCanonicalName(tags) ?: displayName
        val resolvedLat = lat ?: centerLat ?: return emptyList()
        val resolvedLon = lon ?: centerLon ?: return emptyList()
        val elevation = tags["ele"]?.toDoubleOrNull()

        if (entityType != GeoEntityType.PEAK) {
            knownPlaceIds += "${resolvedType.name.lowercase()}/$osmId"
        }

        return listOf(
            ParsedStreamItem.PlaceOrPeak(
                ParsedOsmElement(
                    osmType = resolvedType,
                    osmId = osmId,
                    name = displayName,
                    canonicalName = canonicalName,
                    type = entityType,
                    lat = resolvedLat,
                    lon = resolvedLon,
                    elevation = elevation
                )
            )
        )
    }

    private fun parseAdministrativeBoundaryFootprints(
        members: List<BoundaryMember>,
        knownPlaceIds: Set<String>
    ): List<ParsedStreamItem.Footprint> {
        val matchedEntityIds =
            members
                .asSequence()
                .map { "${it.osmType.name.lowercase()}/${it.ref}" }
                .filter { it in knownPlaceIds }
                .toSet()
        if (matchedEntityIds.isEmpty()) return emptyList()

        val points = members.flatMap { it.geometry }
        val radiusM = PlaceFootprintCalculator.computeRadiusM(points) ?: return emptyList()
        return matchedEntityIds.map { entityId -> ParsedStreamItem.Footprint(entityId = entityId, radiusM = radiusM) }
    }

    private fun parseMembers(reader: JsonReader): List<BoundaryMember> {
        val members = mutableListOf<BoundaryMember>()
        reader.beginArray()
        while (reader.hasNext()) {
            var memberType: OsmType? = null
            var ref = 0L
            var geometry: List<Pair<Double, Double>> = emptyList()
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "type" -> memberType = OsmType.fromTag(reader.nextString())
                    "ref" -> ref = reader.nextLong()
                    "geometry" -> geometry = parseGeometry(reader)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            val resolvedType = memberType ?: continue
            members += BoundaryMember(resolvedType, ref, geometry)
        }
        reader.endArray()
        return members
    }

    private fun parseGeometry(reader: JsonReader): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()
        reader.beginArray()
        while (reader.hasNext()) {
            var lat: Double? = null
            var lon: Double? = null
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "lat" -> lat = reader.nextDouble()
                    "lon" -> lon = reader.nextDouble()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (lat != null && lon != null) {
                points += lat to lon
            }
        }
        reader.endArray()
        return points
    }

    private fun parseCenter(reader: JsonReader): Pair<Double, Double>? {
        var centerLat: Double? = null
        var centerLon: Double? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "lat" -> centerLat = reader.nextDouble()
                "lon" -> centerLon = reader.nextDouble()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        val lat = centerLat ?: return null
        val lon = centerLon ?: return null
        return lat to lon
    }

    private fun parseTags(reader: JsonReader, tags: MutableMap<String, String>) {
        reader.beginObject()
        while (reader.hasNext()) {
            val tagName = reader.nextName()
            when (reader.peek()) {
                JsonReader.Token.STRING -> tags[tagName] = reader.nextString()
                JsonReader.Token.NUMBER -> tags[tagName] = reader.nextDouble().toString()
                JsonReader.Token.BOOLEAN -> tags[tagName] = reader.nextBoolean().toString()
                JsonReader.Token.NULL -> reader.nextNull()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
    }

    private fun resolveEntityType(tags: Map<String, String>): GeoEntityType? {
        tags["natural"]?.let { tag ->
            if (tag.equals("peak", ignoreCase = true)) return GeoEntityType.PEAK
        }
        tags["place"]?.let { tag ->
            GeoEntityType.fromTag(tag)?.let { return it }
        }
        return null
    }

    fun toGeoEntity(element: ParsedOsmElement, seenAt: Instant = Instant.now()): GeoEntity = GeoEntity(
        id = element.entityId,
        osmType = element.osmType,
        // Persist the resolved display label so Explore shows the download-time language even
        // if region_entity_coverage overlay is missing for any reason.
        name = element.name,
        type = element.type,
        lat = element.lat,
        lon = element.lon,
        elevation = element.elevation,
        elevationSource = element.elevation?.let { ElevationSource.OSM_TAG },
        lastSeenAt = seenAt
    )
}
