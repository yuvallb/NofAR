package com.nofar.core.network

/**
 * Ordered Overpass mirror tier list per Requirements §3.2.
 * On 429 or 504, clients retry against the next mirror before surfacing failure.
 */
object OverpassConfig {
    val mirrorBaseUrls: List<String> =
        listOf(
            "https://osm.hpi.de/overpass/api/interpreter",
            "https://overpass-api.de/api/interpreter",
            "https://maps.mail.ru/osm/tools/overpass/api/interpreter"
        )

    const val USER_AGENT: String = "NofAR/0.1 (offline-first hiking AR; Apache-2.0)"
}
