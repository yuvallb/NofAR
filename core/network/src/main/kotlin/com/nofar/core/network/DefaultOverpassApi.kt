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
import okhttp3.Response
import okio.buffer

class DefaultOverpassApi
@Inject
constructor(
    private val okHttpClient: OkHttpClient,
    private val mirrorBaseUrls: List<String> = OverpassConfig.mirrorBaseUrls,
    private val maxOverpassBytes: Long = MAX_OVERPASS_BYTES
) : OverpassApi {
    override suspend fun queryRegion(bbox: BoundingBox, onBytesRead: (Long) -> Unit): OverpassQueryResponse {
        val query = OverpassQueryBuilder.buildQuery(bbox)
        var lastError: IOException? = null

        for ((mirrorIndex, baseUrl) in mirrorBaseUrls.withIndex()) {
            if (mirrorIndex > 0) {
                val backoffMs = (INITIAL_BACKOFF_MS * 2.0.pow(mirrorIndex - 1)).toLong()
                delay(min(backoffMs, MAX_BACKOFF_MS))
            }

            try {
                return executeMirrorQuery(baseUrl, query, onBytesRead)
            } catch (e: OverpassResponseTooLargeException) {
                throw e
            } catch (e: IOException) {
                lastError = e
            }
        }

        throw lastError ?: IOException("All Overpass mirrors failed")
    }

    private suspend fun executeMirrorQuery(
        baseUrl: String,
        query: String,
        onBytesRead: (Long) -> Unit
    ): OverpassQueryResponse {
        val requestBody = FormBody.Builder().add("data", query).build()
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
        return readSuccessfulResponse(response, onBytesRead)
    }

    private fun readSuccessfulResponse(response: Response, onBytesRead: (Long) -> Unit): OverpassQueryResponse {
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw IOException("Overpass mirror returned HTTP $code")
        }

        val datasetVersion = parseDatasetVersion(response.header("Date"))
        val body = response.body
        if (body == null) {
            response.close()
            throw IOException("Empty Overpass response body")
        }

        val contentLength = body.contentLength().takeIf { it >= 0 }
        if (contentLength != null && contentLength > maxOverpassBytes) {
            response.close()
            throw OverpassResponseTooLargeException(
                "Overpass response exceeds size limit " +
                    "($contentLength > $maxOverpassBytes bytes)"
            )
        }

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
                        if (bytesRead > maxOverpassBytes) {
                            throw OverpassResponseTooLargeException(
                                "Overpass response exceeds size limit " +
                                    "($bytesRead > $maxOverpassBytes bytes)"
                            )
                        }
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

    private fun parseDatasetVersion(dateHeader: String?): Instant {
        if (dateHeader.isNullOrBlank()) return Instant.now()
        return runCatching {
            java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.parse(dateHeader, Instant::from)
        }.getOrDefault(Instant.now())
    }

    companion object {
        /** Soft cap on streamed Overpass JSON to avoid OOM / unbounded disk use. */
        const val MAX_OVERPASS_BYTES: Long = 50_000_000L
        private const val INITIAL_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 8_000L
    }
}

/** Non-retryable: response larger than [DefaultOverpassApi.MAX_OVERPASS_BYTES]. */
internal class OverpassResponseTooLargeException(message: String) : IOException(message)
