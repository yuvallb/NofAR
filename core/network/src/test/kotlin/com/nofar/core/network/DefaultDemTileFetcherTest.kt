package com.nofar.core.network

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DefaultDemTileFetcherTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        client = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun fetcher(maxTileBytes: Long = DefaultDemTileFetcher.MAX_TILE_BYTES): DemTileFetcher =
        DefaultDemTileFetcher(
            okHttpClient = client,
            baseUrl = server.url("/dem").toString().trimEnd('/'),
            maxTileBytes = maxTileBytes
        )

    @Test
    fun fetchTile_writesBodyToOutputFile() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("dem-bytes"))
        val out = File(tempFolder.root, "tile.tif")

        fetcher().fetchTile(32, 35, out) { _, _ -> }

        assertThat(out.readText()).isEqualTo("dem-bytes")
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun fetchTile_retriesOnceWithoutRangeOn416() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(416))
        server.enqueue(MockResponse().setResponseCode(200).setBody("full-tile"))
        val out = File(tempFolder.root, "tile.tif")
        File(tempFolder.root, "tile.tif.partial").writeText("stale")

        fetcher().fetchTile(32, 35, out) { _, _ -> }

        assertThat(out.readText()).isEqualTo("full-tile")
        assertThat(server.requestCount).isEqualTo(2)
        assertThat(server.takeRequest().getHeader("Range")).isEqualTo("bytes=5-")
        assertThat(server.takeRequest().getHeader("Range")).isNull()
    }

    @Test
    fun fetchTile_rejectsOversizedContentLength() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("x")
                .setHeader("Content-Length", "100")
        )
        val out = File(tempFolder.root, "tile.tif")

        val error =
            assertThrows(IOException::class.java) {
                runBlocking { fetcher(maxTileBytes = 50).fetchTile(32, 35, out) { _, _ -> } }
            }
        assertThat(error.message).contains("size limit")
    }

    @Test
    fun fetchTile_abortsWhenDownloadedExceedsMax() {
        val body = ByteArray(200) { 1 }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setChunkedBody(Buffer().write(body), 32)
        )
        val out = File(tempFolder.root, "tile.tif")

        val error =
            assertThrows(IOException::class.java) {
                runBlocking { fetcher(maxTileBytes = 100).fetchTile(32, 35, out) { _, _ -> } }
            }
        assertThat(error.message).contains("size limit")
    }

    @Test
    fun fetchTile_rejectsOversizedContentRangeTotal() {
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 0-9/999")
                .setBody("0123456789")
        )
        val out = File(tempFolder.root, "tile.tif")

        val error =
            assertThrows(IOException::class.java) {
                runBlocking { fetcher(maxTileBytes = 50).fetchTile(32, 35, out) { _, _ -> } }
            }
        assertThat(error.message).contains("size limit")
    }

    @Test
    fun fetchTile_throwsWhenCancelled() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("dem-bytes"))
        val out = File(tempFolder.root, "tile.tif")

        assertThrows(CancellationException::class.java) {
            runBlocking {
                fetcher().fetchTile(
                    tileLat = 32,
                    tileLon = 35,
                    outputFile = out,
                    isCancelled = { true }
                ) { _, _ -> }
            }
        }
    }
}
