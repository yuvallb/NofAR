package com.nofar.core.model

/**
 * Ordered Overpass mirror URLs for Prepare downloads and Settings attribution display.
 *
 * Failover order matches Requirements §3.2: HPI → overpass-api.de.
 */
object OverpassMirrors {
    val baseUrls: List<String> =
        listOf(
            "https://osm.hpi.de/overpass/api/interpreter",
            "https://overpass-api.de/api/interpreter"
        )
}
