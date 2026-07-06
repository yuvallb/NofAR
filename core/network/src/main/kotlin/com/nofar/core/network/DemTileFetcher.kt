@file:Suppress("CyclomaticComplexMethod", "NestedBlockDepth")

package com.nofar.core.network

import com.nofar.core.model.DemTileId
import java.io.File
import java.io.IOException
import javax.inject.Inject
import okhttp3.OkHttpClient
import okhttp3.Request

interface DemTileFetcher {
    suspend fun fetchTile(
        tileLat: Int,
        tileLon: Int,
        outputFile: File,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit
    )

    fun tileUrl(tileLat: Int, tileLon: Int): String
}

class DefaultDemTileFetcher
@Inject
constructor(private val okHttpClient: OkHttpClient) : DemTileFetcher {
    override fun tileUrl(tileLat: Int, tileLon: Int): String {
        val tileId = DemTileId.fromCoordinates(tileLat, tileLon)
        return "$COPERNICUS_BASE_URL/${DemTileId.s3ObjectKey(tileId)}"
    }

    override suspend fun fetchTile(
        tileLat: Int,
        tileLon: Int,
        outputFile: File,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit
    ) {
        outputFile.parentFile?.mkdirs()
        val tempFile = File(outputFile.parentFile, "${outputFile.name}.partial")
        val existingBytes = if (tempFile.exists()) tempFile.length() else 0L

        val requestBuilder =
            Request.Builder()
                .url(tileUrl(tileLat, tileLon))
                .header("User-Agent", OverpassConfig.USER_AGENT)

        if (existingBytes > 0) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }

        val response = okHttpClient.newCall(requestBuilder.build()).execute()
        response.use { httpResponse ->
            if (httpResponse.code == HTTP_RANGE_NOT_SATISFIABLE) {
                tempFile.delete()
                return fetchTile(tileLat, tileLon, outputFile, onProgress)
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
                    else -> body.contentLength().takeIf { it >= 0 }?.let { it + if (append) existingBytes else 0 }
                }

            body.byteStream().use { input ->
                java.io.FileOutputStream(tempFile, append).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var downloaded = if (append) existingBytes else 0L
                    onProgress(downloaded, totalBytes)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, totalBytes)
                    }
                }
            }

            if (tempFile.renameTo(outputFile).not()) {
                tempFile.copyTo(outputFile, overwrite = true)
                tempFile.delete()
            }
        }
    }

    companion object {
        private const val COPERNICUS_BASE_URL =
            "https://copernicus-dem-30m.s3.eu-central-1.amazonaws.com"
        private const val HTTP_PARTIAL_CONTENT = 206
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
        private const val BUFFER_SIZE = 8192
    }
}
