package com.nofar.core.network

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.GeoMathBounds
import org.junit.Test

class OverpassQueryBuilderTest {
    @Test
    fun boundingBoxFromCircle_matchesGeoMathBounds() {
        val bbox = OverpassQueryBuilder.boundingBoxFromCircle(32.0, 35.0, 10_000.0)
        val base = GeoMathBounds.boundingBox(32.0, 35.0, 10_000.0)
        assertThat(bbox).isEqualTo(base)
    }

    @Test
    fun boundingBoxForCell_coversExactOneDegree() {
        val bbox = OverpassQueryBuilder.boundingBoxForCell(32, 35)
        assertThat(bbox.minLat).isEqualTo(32.0)
        assertThat(bbox.maxLat).isEqualTo(33.0)
        assertThat(bbox.minLon).isEqualTo(35.0)
        assertThat(bbox.maxLon).isEqualTo(36.0)
    }

    @Test
    fun buildQuery_containsPlaceAndPeakSelectorsOnly() {
        val bbox = GeoMathBounds.boundingBox(32.0, 35.0, 10_000.0)
        val query = OverpassQueryBuilder.buildQuery(bbox)
        assertThat(query).contains("node[\"place\"~\"city|town|village\"")
        assertThat(query).contains("node[\"natural\"=\"peak\"]")
        assertThat(query).contains("way[\"place\"~")
        assertThat(query).contains("relation[\"natural\"=\"peak\"]")
        assertThat(query).contains(")->.places;")
        assertThat(query).contains(")->.peaks;")
        assertThat(query).contains(".places out center;")
        assertThat(query).contains(".peaks out center;")
        assertThat(query).doesNotContain("boundary")
        assertThat(query).doesNotContain("out geom")
        assertThat(query).contains("${bbox.minLat},${bbox.minLon},${bbox.maxLat},${bbox.maxLon}")
    }
}
