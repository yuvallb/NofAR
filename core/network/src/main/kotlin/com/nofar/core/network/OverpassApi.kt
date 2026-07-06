package com.nofar.core.network

import com.nofar.core.model.BoundingBox
import java.io.InputStream
import java.time.Instant

data class OverpassQueryResponse(val body: InputStream, val datasetVersion: Instant, val contentLength: Long?)

data class OverpassResult(val datasetVersion: Instant, val bytesRead: Long)

interface OverpassApi {
    /**
     * Executes an Overpass query for [bbox], streaming the response body.
     * Caller must close [OverpassQueryResponse.body].
     */
    suspend fun queryRegion(bbox: BoundingBox, onBytesRead: (Long) -> Unit): OverpassQueryResponse
}
