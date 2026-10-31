package top.saltfish.ech0.data.net

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CallAwaitTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `await returns response and body`() = runTest {
        server.enqueue(MockResponse().setBody("ok"))

        val response = client.newCall(
            Request.Builder().url(server.url("/api/hello")).build()
        ).await()

        response.use {
            assertEquals(200, it.code)
            assertEquals("ok", it.body?.string())
        }
    }

    @Test
    fun `await surfaces http errors`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("no"))
        val response = client.newCall(
            Request.Builder().url(server.url("/api/user")).build()
        ).await()
        response.use { assertEquals(401, it.code) }
    }
}
