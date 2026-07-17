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
)

/**
 * Streams Overpass JSON elements one-by-one without loading the full response into memory.
 */
class OverpassStreamParser {
    fun parse(input: InputStream, onElement: (ParsedOsmElement) -> Unit): Int =
        parse(input, LabelLanguage.DEFAULT, onElement)

    fun parse(input: InputStream, labelLanguage: LabelLanguage, onElement: (ParsedOsmElement) -> Unit): Int {
        val reader = input.source().buffer().let { JsonReader.of(it) }
        var count = 0
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "elements" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        parseElement(reader, labelLanguage)?.let { element ->
                            onElement(element)
                            count++
                        }
                    }
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return count
    }

    private fun parseElement(reader: JsonReader, labelLanguage: LabelLanguage): ParsedOsmElement? {
        var osmType: OsmType? = null
        var osmId = 0L
        var lat: Double? = null
        var lon: Double? = null
        var centerLat: Double? = null
        var centerLon: Double? = null
        val tags = mutableMapOf<String, String>()

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
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val resolvedType = osmType ?: return null
        val entityType = resolveEntityType(tags) ?: return null
        val displayName = OsmNameResolver.resolveDisplayName(tags, labelLanguage) ?: return null
        val canonicalName = OsmNameResolver.resolveCanonicalName(tags) ?: displayName
        val resolvedLat = lat ?: centerLat ?: return null
        val resolvedLon = lon ?: centerLon ?: return null
        val elevation = tags["ele"]?.toDoubleOrNull()

        return ParsedOsmElement(
            osmType = resolvedType,
            osmId = osmId,
            name = displayName,
            canonicalName = canonicalName,
            type = entityType,
            lat = resolvedLat,
            lon = resolvedLon,
            elevation = elevation
        )
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
        id = "${element.osmType.name.lowercase()}/${element.osmId}",
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
