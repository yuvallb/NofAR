@file:Suppress("ThrowsCount")

package com.nofar.core.network

import com.nofar.core.model.BoundingBox
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer

class DefaultOverpassApi
@Inject
constructor(
    private val okHttpClient: OkHttpClient,
    private val mirrorBaseUrls: List<String> = OverpassConfig.mirrorBaseUrls
) : OverpassApi {
    override suspend fun queryRegion(bbox: BoundingBox, onBytesRead: (Long) -> Unit): OverpassQueryResponse {
        val query = OverpassQueryBuilder.buildQuery(bbox)
        var lastError: IOException? = null

        for ((mirrorIndex, baseUrl) in mirrorBaseUrls.withIndex()) {
            if (mirrorIndex > 0) {
                val backoffMs = (INITIAL_BACKOFF_MS * 2.0.pow(mirrorIndex - 1)).toLong()
                delay(min(backoffMs, MAX_BACKOFF_MS))
            }

            val requestBody =
                FormBody.Builder()
                    .add("data", query)
                    .build()

            val request =
                Request.Builder()
                    .url(baseUrl)
                    .header("User-Agent", OverpassConfig.USER_AGENT)
                    .post(requestBody)
                    .build()

            val response =
                withContext(Dispatchers.IO) {
                    okHttpClient.newCall(request).execute()
                }
            when (response.code) {
                HTTP_TOO_MANY_REQUESTS, HTTP_GATEWAY_TIMEOUT -> {
                    lastError = IOException("Overpass mirror returned HTTP ${response.code}")
                    response.close()
                    continue
                }
            }

            if (!response.isSuccessful) {
                response.close()
                throw IOException("Overpass request failed: HTTP ${response.code}")
            }

            val datasetVersion = parseDatasetVersion(response.header("Date"))
            val body = response.body ?: run {
                response.close()
                throw IOException("Empty Overpass response body")
            }
            val contentLength = body.contentLength().takeIf { it >= 0 }
            val trackingSource = body.source()
            var bytesRead = 0L

            val countingSource =
                object : okio.Source {
                    override fun close() {
                        trackingSource.close()
                    }

                    override fun read(sink: okio.Buffer, byteCount: Long): Long {
                        val read = trackingSource.read(sink, byteCount)
                        if (read > 0) {
                            bytesRead += read
                            onBytesRead(bytesRead)
                        }
                        return read
                    }

                    override fun timeout(): okio.Timeout = trackingSource.timeout()
                }

            return OverpassQueryResponse(
                body = countingSource.buffer().inputStream(),
                datasetVersion = datasetVersion,
                contentLength = contentLength
            )
        }

        throw lastError ?: IOException("All Overpass mirrors failed")
    }

    private fun parseDatasetVersion(dateHeader: String?): Instant {
        if (dateHeader.isNullOrBlank()) return Instant.now()
        return runCatching {
            java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.parse(dateHeader, Instant::from)
        }.getOrDefault(Instant.now())
    }

    companion object {
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_GATEWAY_TIMEOUT = 504
        private const val INITIAL_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 8_000L
    }
}
