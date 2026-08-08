@file:Suppress(
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LoopWithTooManyJumpStatements",
    "LongMethod",
    "ThrowsCount"
)

package com.nofar.core.network

import com.nofar.core.model.DemTileId
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

interface DemTileFetcher {
    suspend fun fetchTile(
        tileLat: Int,
        tileLon: Int,
        outputFile: File,
        isCancelled: () -> Boolean = { false },
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit
    )

    fun tileUrl(tileLat: Int, tileLon: Int): String
}

class DefaultDemTileFetcher(
    private val okHttpClient: OkHttpClient,
    private val baseUrl: String = COPERNICUS_BASE_URL,
    private val maxTileBytes: Long = MAX_TILE_BYTES
) : DemTileFetcher {
    @Inject
    constructor(okHttpClient: OkHttpClient) : this(
        okHttpClient,
        COPERNICUS_BASE_URL,
        MAX_TILE_BYTES
    )
    override fun tileUrl(tileLat: Int, tileLon: Int): String {
        val tileId = DemTileId.fromCoordinates(tileLat, tileLon)
        return "$baseUrl/${DemTileId.s3ObjectKey(tileId)}"
    }

    override suspend fun fetchTile(
        tileLat: Int,
        tileLon: Int,
        outputFile: File,
        isCancelled: () -> Boolean,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            outputFile.parentFile?.mkdirs()
            val tempFile = File(outputFile.parentFile, "${outputFile.name}.partial")

            var attempt = 0
            while (attempt < MAX_ATTEMPTS) {
                attempt++
                if (isCancelled()) {
                    throw CancellationException("DEM download cancelled")
                }

                val existingBytes = if (tempFile.exists()) tempFile.length() else 0L
                if (existingBytes > maxTileBytes) {
                    tempFile.delete()
                    throw IOException(
                        "DEM partial file exceeds size limit ($existingBytes > $maxTileBytes bytes)"
                    )
                }

                val requestBuilder =
                    Request.Builder()
                        .url(tileUrl(tileLat, tileLon))
                        .header("User-Agent", OverpassConfig.USER_AGENT)

                if (existingBytes > 0) {
                    requestBuilder.header("Range", "bytes=$existingBytes-")
                }

                val response = okHttpClient.newCall(requestBuilder.build()).execute()
                var retryWithoutRange = false
                response.use { httpResponse ->
                    if (httpResponse.code == HTTP_RANGE_NOT_SATISFIABLE) {
                        tempFile.delete()
                        retryWithoutRange = true
                        return@use
                    }
                    if (!httpResponse.isSuccessful) {
                        throw IOException("DEM download failed: HTTP ${httpResponse.code}")
                    }

                    val body = httpResponse.body ?: throw IOException("Empty DEM response body")
                    val append = httpResponse.code == HTTP_PARTIAL_CONTENT && existingBytes > 0
                    val totalBytes =
                        when {
                            httpResponse.header("Content-Range") != null -> {
                                httpResponse.header("Content-Range")
                                    ?.substringAfter("/")
                                    ?.toLongOrNull()
                            }
                            else ->
                                body.contentLength().takeIf { it >= 0 }?.let {
                                    it + if (append) existingBytes else 0
                                }
                        }

                    if (totalBytes != null && totalBytes > maxTileBytes) {
                        throw IOException(
                            "DEM tile exceeds size limit ($totalBytes > $maxTileBytes bytes)"
                        )
                    }

                    val contentLength = body.contentLength().takeIf { it >= 0 }
                    if (contentLength != null && !append && contentLength > maxTileBytes) {
                        throw IOException(
                            "DEM tile exceeds size limit ($contentLength > $maxTileBytes bytes)"
                        )
                    }

                    body.byteStream().use { input ->
                        java.io.FileOutputStream(tempFile, append).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var downloaded = if (append) existingBytes else 0L
                            onProgress(downloaded, totalBytes)
                            while (true) {
                                if (isCancelled()) {
                                    throw CancellationException("DEM download cancelled")
                                }
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                if (downloaded > maxTileBytes) {
                                    throw IOException(
                                        "DEM tile exceeds size limit " +
                                            "($downloaded > $maxTileBytes bytes)"
                                    )
                                }
                                onProgress(downloaded, totalBytes)
                            }
                        }
                    }

                    if (tempFile.renameTo(outputFile).not()) {
                        tempFile.copyTo(outputFile, overwrite = true)
                        tempFile.delete()
                    }
                    return@withContext
                }
                if (!retryWithoutRange) {
                    break
                }
            }

            throw IOException("DEM download failed after $MAX_ATTEMPTS attempts")
        }
    }

    companion object {
        private const val COPERNICUS_BASE_URL =
            "https://copernicus-dem-30m.s3.eu-central-1.amazonaws.com"

        /** Soft cap on a single Copernicus DEM GeoTIFF download. */
        const val MAX_TILE_BYTES: Long = 80L * 1024 * 1024
        private const val HTTP_PARTIAL_CONTENT = 206
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
        private const val BUFFER_SIZE = 8192
        private const val MAX_ATTEMPTS = 2
    }
}
