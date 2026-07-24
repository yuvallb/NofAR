package com.nofar.core.data.osm

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.GeoEntityType
import com.nofar.core.model.LabelLanguage
import com.nofar.core.model.OsmType
import org.junit.Test

class OverpassStreamParserTest {
    private val parser = OverpassStreamParser()

    @Test
    fun parse_streamsElementsWithoutLoadingFullDocument() {
        val fixture = javaClass.getResourceAsStream("/overpass_fixture.json")!!
        var peakCount = 0
        val count =
            parser.parse(fixture) { element ->
                if (element.type == GeoEntityType.PEAK) peakCount++
            }
        assertThat(count).isAtLeast(100)
        assertThat(peakCount).isGreaterThan(0)
    }

    @Test
    fun parse_skipsUnnamedEntities() {
        val json =
            """
            {"elements":[
              {"type":"node","id":1,"lat":32.0,"lon":35.0,"tags":{"natural":"peak"}},
              {"type":"node","id":2,"lat":32.1,"lon":35.1,"tags":{"natural":"peak","name":"Mount Test"}}
            ]}
            """.trimIndent()
        val parsed = mutableListOf<ParsedOsmElement>()
        val count = parser.parse(json.byteInputStream(), parsed::add)
        assertThat(count).isEqualTo(1)
        assertThat(parsed.single().name).isEqualTo("Mount Test")
    }

    @Test
    fun parse_hebrew_usesLocalizedNameWithFallback() {
        val json =
            """
            {"elements":[
              {"type":"node","id":1,"lat":32.0,"lon":35.0,"tags":{"natural":"peak","name":"Mount Test","name:he":"הר בדיקה"}},
              {"type":"node","id":2,"lat":32.1,"lon":35.1,"tags":{"natural":"peak","name":"Only English"}},
              {"type":"node","id":3,"lat":32.2,"lon":35.2,"tags":{"natural":"peak","name:he":"רק עברית"}}
            ]}
            """.trimIndent()
        val parsed = mutableListOf<ParsedOsmElement>()
        val count = parser.parse(json.byteInputStream(), LabelLanguage.HEBREW, parsed::add)
        assertThat(count).isEqualTo(3)
        assertThat(parsed[0].name).isEqualTo("הר בדיקה")
        assertThat(parsed[0].canonicalName).isEqualTo("Mount Test")
        assertThat(parsed[1].name).isEqualTo("Only English")
        assertThat(parsed[2].name).isEqualTo("רק עברית")
        assertThat(parsed[2].canonicalName).isEqualTo("רק עברית")
    }

    @Test
    fun parse_english_prefersNameEnOverHebrewPrimaryName() {
        val json =
            """
            {"elements":[
              {"type":"node","id":1,"lat":32.0,"lon":35.0,"tags":{"natural":"peak","name":"הר חרמון","name:he":"הר חרמון","name:en":"Mount Hermon"}}
            ]}
            """.trimIndent()
        val parsed = mutableListOf<ParsedOsmElement>()
        parser.parse(json.byteInputStream(), LabelLanguage.ENGLISH, parsed::add)
        assertThat(parsed.single().name).isEqualTo("Mount Hermon")
        assertThat(parsed.single().canonicalName).isEqualTo("הר חרמון")
        assertThat(parser.toGeoEntity(parsed.single()).name).isEqualTo("Mount Hermon")
    }

    @Test
    fun toGeoEntity_usesDisplayName() {
        val element =
            ParsedOsmElement(
                osmType = OsmType.NODE,
                osmId = 42,
                name = "חיפה",
                canonicalName = "Haifa",
                type = GeoEntityType.CITY,
                lat = 32.8,
                lon = 34.98,
                elevation = 10.0
            )
        val entity = parser.toGeoEntity(element)
        assertThat(entity.id).isEqualTo("node/42")
        assertThat(entity.name).isEqualTo("חיפה")
        assertThat(entity.type).isEqualTo(GeoEntityType.CITY)
    }

    @Test
    fun parse_boundaryRelationMatchingPlace_emitsFootprint() {
        val json =
            """
            {"elements":[
              {"type":"node","id":100,"lat":32.0,"lon":35.0,"tags":{"place":"city","name":"Test City"}},
              {"type":"relation","id":200,"tags":{"boundary":"administrative","admin_level":"8"},
               "members":[
                 {"type":"node","ref":100,"role":"label"},
                 {"type":"way","ref":300,"role":"outer","geometry":[
                   {"lat":32.00,"lon":35.00},
                   {"lat":32.02,"lon":35.00},
                   {"lat":32.02,"lon":35.02},
                   {"lat":32.00,"lon":35.02},
                   {"lat":32.00,"lon":35.00}
                 ]}
               ]}
            ]}
            """.trimIndent()
        val footprints = mutableListOf<Pair<String, Double>>()
        val count =
            parser.parse(
                input = json.byteInputStream(),
                labelLanguage = LabelLanguage.DEFAULT,
                onElement = {},
                onFootprint = { entityId, radiusM -> footprints += entityId to radiusM }
            )
        assertThat(count).isEqualTo(1)
        assertThat(footprints).hasSize(1)
        assertThat(footprints.single().first).isEqualTo("node/100")
        assertThat(footprints.single().second).isGreaterThan(0.0)
    }

    @Test
    fun parse_unmatchedBoundaryRelation_ignored() {
        val json =
            """
            {"elements":[
              {"type":"relation","id":200,"tags":{"boundary":"administrative"},
               "members":[
                 {"type":"way","ref":300,"role":"outer","geometry":[{"lat":32.0,"lon":35.0},{"lat":32.1,"lon":35.1}]}
               ]}
            ]}
            """.trimIndent()
        val footprints = mutableListOf<Pair<String, Double>>()
        val count =
            parser.parse(
                input = json.byteInputStream(),
                labelLanguage = LabelLanguage.DEFAULT,
                onElement = {},
                onFootprint = { id, r -> footprints += id to r }
            )
        assertThat(count).isEqualTo(0)
        assertThat(footprints).isEmpty()
    }

    @Test
    fun parse_twoBoundariesForSamePlace_keepsSmallestRadius() {
        val json =
            """
            {"elements":[
              {"type":"node","id":100,"lat":32.0,"lon":35.0,"tags":{"place":"city","name":"Test City"}},
              {"type":"relation","id":201,"tags":{"boundary":"administrative"},
               "members":[
                 {"type":"node","ref":100,"role":"label"},
                 {"type":"way","ref":301,"role":"outer","geometry":[
                   {"lat":32.00,"lon":35.00},{"lat":32.10,"lon":35.00},{"lat":32.10,"lon":35.10},{"lat":32.00,"lon":35.10}
                 ]}
               ]},
              {"type":"relation","id":202,"tags":{"boundary":"administrative"},
               "members":[
                 {"type":"node","ref":100,"role":"label"},
                 {"type":"way","ref":302,"role":"outer","geometry":[
                   {"lat":32.00,"lon":35.00},{"lat":32.02,"lon":35.00},{"lat":32.02,"lon":35.02},{"lat":32.00,"lon":35.02}
                 ]}
               ]}
            ]}
            """.trimIndent()
        val footprints = mutableMapOf<String, Double>()
        parser.parse(
            input = json.byteInputStream(),
            labelLanguage = LabelLanguage.DEFAULT,
            onElement = {},
            onFootprint = { id, r -> footprints[id] = r }
        )
        assertThat(footprints).hasSize(1)
        assertThat(footprints["node/100"]).isNotNull()
    }
}
