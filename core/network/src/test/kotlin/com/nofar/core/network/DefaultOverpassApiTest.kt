package com.nofar.core.network

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.BoundingBox
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class DefaultOverpassApiTest {
    private lateinit var server1: MockWebServer
    private lateinit var server2: MockWebServer
    private lateinit var api: OverpassApi

    @Before
    fun setUp() {
        server1 = MockWebServer().also { it.start() }
        server2 = MockWebServer().also { it.start() }
        val client = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()
        api =
            DefaultOverpassApi(
                okHttpClient = client,
                mirrorBaseUrls = listOf(server1.url("/").toString(), server2.url("/").toString())
            )
    }

    @After
    fun tearDown() {
        server1.shutdown()
        server2.shutdown()
    }

    @Test
    fun queryRegion_retriesSecondMirrorOn429() = runBlocking {
        server1.enqueue(MockResponse().setResponseCode(429))
        server2.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"elements":[]}""")
                .addHeader("Date", "Sun, 05 Jul 2026 10:00:00 GMT")
        )

        val bbox = BoundingBox(31.0, 34.0, 33.0, 36.0)
        val response = api.queryRegion(bbox) {}
        response.body.use { stream ->
            assertThat(String(stream.readBytes())).contains("elements")
        }
        assertThat(server1.requestCount).isEqualTo(1)
        assertThat(server2.requestCount).isEqualTo(1)
    }
}
