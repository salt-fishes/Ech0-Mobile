package top.saltfish.ech0.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ImageBedUploadTest {

    @Test
    fun `upload sends superbed multipart fields and returns the url`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"err":0,"url":"https://icdn.example.com/x.png"}""")
        )
        server.start()
        val file = File.createTempFile("ech0-bed", ".png").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        try {
            val config = ImageBedConfig(
                enabled = true,
                token = "token-123",
                album = "旅行",
                endpoint = server.url("/upload").toString()
            )
            val url = ImageBedClient.upload(file, "image/png", config)
            assertEquals("https://icdn.example.com/x.png", url)

            val request = server.takeRequest()
            assertEquals("/upload", request.path)
            assertEquals("POST", request.method)
            // Superbed 只认表单 token，不再发送 X-API-Key 头
            assertNull(request.getHeader("X-API-Key"))

            val body = request.body.readUtf8()
            assertTrue(body.contains("name=\"file\""))
            assertTrue(body.contains("name=\"token\""))
            assertTrue(body.contains("name=\"categories\""))
            assertTrue(body.contains("token-123"))
            assertTrue(body.contains("旅行"))
        } finally {
            server.shutdown()
            file.delete()
        }
    }

    @Test
    fun `easyimage can upload without token`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"code":200,"url":"https://i2.example.com/a.png"}""")
        )
        server.start()
        val file = File.createTempFile("ech0-bed", ".png").apply {
            writeBytes(byteArrayOf(9, 9))
        }
        try {
            val config = ImageBedConfig()
                .applyPreset(ImageBedPreset.EasyImage)
                .copy(endpoint = server.url("/api/index.php").toString())
            val url = ImageBedClient.upload(file, "image/png", config)
            assertEquals("https://i2.example.com/a.png", url)

            val body = server.takeRequest().body.readUtf8()
            assertTrue(body.contains("name=\"image\""))
            // Token 为空时不应发送空的 token 字段
            assertFalse(body.contains("name=\"token\""))
        } finally {
            server.shutdown()
            file.delete()
        }
    }

    @Test(expected = ApiException::class)
    fun `superbed without token fails before touching the network`() {
        val file = File.createTempFile("ech0-bed", ".png").apply { writeText("x") }
        try {
            runBlocking<Unit> {
                ImageBedClient.upload(file, "image/png", ImageBedConfig(enabled = true, token = ""))
            }
        } finally {
            file.delete()
        }
    }
}
