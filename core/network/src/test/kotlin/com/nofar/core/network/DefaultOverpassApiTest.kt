package com.nofar.core.network

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.BoundingBox
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class DefaultOverpassApiTest {
    private lateinit var server1: MockWebServer
    private lateinit var server2: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server1 = MockWebServer().also { it.start() }
        server2 = MockWebServer().also { it.start() }
        client = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()
    }

    @After
    fun tearDown() {
        server1.shutdown()
        server2.shutdown()
    }

    private fun api(maxOverpassBytes: Long = DefaultOverpassApi.MAX_OVERPASS_BYTES): OverpassApi = DefaultOverpassApi(
        okHttpClient = client,
        mirrorBaseUrls = listOf(server1.url("/").toString(), server2.url("/").toString()),
        maxOverpassBytes = maxOverpassBytes
    )

    @Test
    fun queryRegion_retriesSecondMirrorOn429() = runBlocking {
        server1.enqueue(MockResponse().setResponseCode(429))
        server2.enqueue(successBody())

        val response = api().queryRegion(testBbox) {}
        response.body.use { stream ->
            assertThat(String(stream.readBytes())).contains("elements")
        }
        assertThat(server1.requestCount).isEqualTo(1)
        assertThat(server2.requestCount).isEqualTo(1)
    }

    @Test
    fun queryRegion_retriesSecondMirrorOn502() = runBlocking {
        server1.enqueue(MockResponse().setResponseCode(502))
        server2.enqueue(successBody())

        val response = api().queryRegion(testBbox) {}
        response.body.use { stream ->
            assertThat(String(stream.readBytes())).contains("elements")
        }
        assertThat(server2.requestCount).isEqualTo(1)
    }

    @Test
    fun queryRegion_retriesSecondMirrorOn4xx() = runBlocking {
        server1.enqueue(MockResponse().setResponseCode(400))
        server2.enqueue(successBody())

        val response = api().queryRegion(testBbox) {}
        response.body.use { stream ->
            assertThat(String(stream.readBytes())).contains("elements")
        }
        assertThat(server1.requestCount).isEqualTo(1)
        assertThat(server2.requestCount).isEqualTo(1)
    }

    @Test
    fun queryRegion_retriesSecondMirrorOnIoException() = runBlocking {
        server1.shutdown()
        server2.enqueue(successBody())

        val response = api().queryRegion(testBbox) {}
        response.body.use { stream ->
            assertThat(String(stream.readBytes())).contains("elements")
        }
        assertThat(server2.requestCount).isEqualTo(1)
    }

    @Test
    fun queryRegion_rejectsOversizedContentLength() {
        server1.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("x")
                .setHeader("Content-Length", "1000")
        )

        val error =
            assertThrows(IOException::class.java) {
                runBlocking { api(maxOverpassBytes = 100).queryRegion(testBbox) {} }
            }
        assertThat(error).isInstanceOf(OverpassResponseTooLargeException::class.java)
        assertThat(server2.requestCount).isEqualTo(0)
    }

    @Test
    fun queryRegion_rejectsWhenStreamExceedsMaxBytes() {
        val oversized = ByteArray(200) { 1 }
        server1.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setChunkedBody(Buffer().write(oversized), 32)
        )

        val error =
            assertThrows(IOException::class.java) {
                runBlocking {
                    api(maxOverpassBytes = 100).queryRegion(testBbox) {}.body.use { it.readBytes() }
                }
            }
        assertThat(error).isInstanceOf(OverpassResponseTooLargeException::class.java)
    }

    @Test
    fun queryRegion_throwsWhenAllMirrorsFail() {
        server1.enqueue(MockResponse().setResponseCode(503))
        server2.enqueue(MockResponse().setResponseCode(504))

        val error =
            assertThrows(IOException::class.java) {
                runBlocking { api().queryRegion(testBbox) {} }
            }
        assertThat(error.message).contains("HTTP 504")
    }

    private fun successBody(): MockResponse = MockResponse()
        .setResponseCode(200)
        .setBody("""{"elements":[]}""")
        .addHeader("Date", "Sun, 05 Jul 2026 10:00:00 GMT")

    private companion object {
        val testBbox = BoundingBox(31.0, 34.0, 33.0, 36.0)
    }
}
