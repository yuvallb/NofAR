package com.nofar.core.network

import com.nofar.core.model.OverpassMirrors

/**
 * Ordered Overpass mirror tier list per Requirements §3.2.
 *
 * Failover order: HPI → overpass-api.de/
 *
 * Privacy: each query POSTs an Overpass QL string that includes the region bbox (and thus an
 * approximate location). On failover, that same query is retried against the next mirror.

 * [DefaultOverpassApi] fails over on I/O errors and any non-success HTTP response (including 429
 * and 5xx) until mirrors are exhausted.
 */
object OverpassConfig {
    val mirrorBaseUrls: List<String> = OverpassMirrors.baseUrls

    const val USER_AGENT: String = "NofAR/0.1 (offline-first hiking AR; Apache-2.0)"
}
