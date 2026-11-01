package top.saltfish.ech0.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageBedTest {

    private val superbed = ImageBedConfig(token = "t")
    private val lsky = ImageBedConfig(token = "t").applyPreset(ImageBedPreset.Lsky)
    private val easyImage = ImageBedConfig(token = "t").applyPreset(ImageBedPreset.EasyImage)
    private val see = ImageBedConfig(token = "t").applyPreset(ImageBedPreset.See)
    private val smms = ImageBedConfig(token = "t").applyPreset(ImageBedPreset.Smms)

    @Test
    fun `superbed success response returns top level url`() {
        val body = """{"err":0,"url":"https://icdn.example.com/a.png"}"""
        assertEquals(
            "https://icdn.example.com/a.png",
            ImageBedClient.parseUploadResponse(body, superbed)
        )
    }

    @Test
    fun `lsky success reads nested links url`() {
        val body = """{"status":true,"data":{"key":"a","links":{"url":"https://img.example.com/a.jpg"}}}"""
        assertEquals(
            "https://img.example.com/a.jpg",
            ImageBedClient.parseUploadResponse(body, lsky)
        )
    }

    @Test
    fun `easyimage success reads code and url`() {
        val body = """{"result":"success","code":200,"url":"https://i2.example.com/a.webp"}"""
        assertEquals(
            "https://i2.example.com/a.webp",
            ImageBedClient.parseUploadResponse(body, easyImage)
        )
    }

    @Test
    fun `see success reads data url`() {
        val body = """{"success":true,"code":"success","data":{"url":"https://s.ee/a.png"}}"""
        assertEquals(
            "https://s.ee/a.png",
            ImageBedClient.parseUploadResponse(body, see)
        )
    }

    @Test
    fun `smms success reads data url`() {
        val body = """{"success":true,"code":"success","data":{"url":"https://s2.loli.net/a.png"}}"""
        assertEquals(
            "https://s2.loli.net/a.png",
            ImageBedClient.parseUploadResponse(body, smms)
        )
    }

    @Test(expected = ApiException::class)
    fun `superbed non zero err is reported`() {
        ImageBedClient.parseUploadResponse("""{"err":1,"message":"token 无效"}""", superbed)
    }

    @Test(expected = ApiException::class)
    fun `lsky status false is reported with message`() {
        ImageBedClient.parseUploadResponse(
            """{"status":false,"message":"未授权"}""",
            lsky
        )
    }

    @Test(expected = ApiException::class)
    fun `easyimage non 200 code is reported`() {
        ImageBedClient.parseUploadResponse("""{"code":500,"message":"空间不足"}""", easyImage)
    }

    @Test
    fun `missing status field falls back to url`() {
        // 有些图床成功响应省略状态字段，只要取得到 url 就算成功
        val body = """{"url":"https://icdn.example.com/b.png"}"""
        assertEquals(
            "https://icdn.example.com/b.png",
            ImageBedClient.parseUploadResponse(body, superbed)
        )
    }

    @Test(expected = ApiException::class)
    fun `missing url is rejected`() {
        ImageBedClient.parseUploadResponse("""{"code":200}""", easyImage)
    }

    @Test(expected = ApiException::class)
    fun `relative url is rejected`() {
        ImageBedClient.parseUploadResponse("""{"err":0,"url":"/images/a.png"}""", superbed)
    }

    @Test(expected = ApiException::class)
    fun `non json body is rejected`() {
        ImageBedClient.parseUploadResponse("<html>502</html>", superbed)
    }

    @Test
    fun `array path is supported`() {
        val config = ImageBedConfig(urlPath = "data.images.0.url", successPath = "")
        val body = """{"data":{"images":[{"url":"https://icdn.example.com/c.jpg"}]}}"""
        assertEquals(
            "https://icdn.example.com/c.jpg",
            ImageBedClient.parseUploadResponse(body, config)
        )
    }

    @Test
    fun `preset switching clears credentials`() {
        val switched = ImageBedConfig(
            token = "secret",
            album = "相册",
            queryTokenField = "key"
        ).applyPreset(ImageBedPreset.Lsky)
        assertEquals("lsky", switched.preset)
        // 旧凭证不能带到新图床，否则可能把 Key 发给第三方
        assertEquals("", switched.token)
        assertEquals("", switched.album)
        assertEquals("", switched.queryTokenField)
        assertEquals("file", switched.fileField)
        assertEquals("Bearer ", switched.headerPrefix)
        assertEquals("data.links.url", switched.urlPath)
    }

    @Test
    fun `switching preset disables upload switch`() {
        val switched = ImageBedConfig(enabled = true, token = "secret")
            .withPresetDefaults(ImageBedPreset.Lsky)
        assertEquals(false, switched.enabled)
        assertEquals("", switched.token)
        assertEquals("lsky", switched.preset)
    }

    @Test
    fun `superbed no longer sends api key header`() {
        assertEquals("", ImageBedPreset.Superbed.headerName)
        assertEquals("token", ImageBedPreset.Superbed.formTokenField)
    }

    @Test
    fun `smms preset has no bearer prefix`() {
        assertEquals("Authorization", ImageBedPreset.Smms.headerName)
        assertEquals("", ImageBedPreset.Smms.headerPrefix)
        assertEquals("Bearer ", ImageBedPreset.See.headerPrefix)
    }

    @Test
    fun `easyimage token is optional`() {
        assertEquals(false, ImageBedPreset.EasyImage.tokenRequired)
        assertEquals(true, ImageBedPreset.Superbed.tokenRequired)
    }

    @Test
    fun `invalid extra fields json is rejected`() {
        val error = runCatching { ImageBedClient.extraFields("{not json") }.exceptionOrNull()
        assertEquals(ApiException::class.java, error?.javaClass)
    }

    @Test
    fun `extra fields must be an object`() {
        val error = runCatching { ImageBedClient.extraFields("[1,2]") }.exceptionOrNull()
        assertEquals(ApiException::class.java, error?.javaClass)
    }
}
