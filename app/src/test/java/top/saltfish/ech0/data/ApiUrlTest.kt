package top.saltfish.ech0.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.saltfish.ech0.data.net.HttpClients

class ApiUrlTest {

    @Test
    fun `relative path is joined to base`() {
        assertEquals(
            "https://example.com/base/api/files/a.jpg",
            Ech0Api.resolveUrlWithBase("https://example.com/base/", "/api/files/a.jpg")
        )
    }

    @Test
    fun `protocol relative url keeps base scheme`() {
        assertEquals(
            "https://cdn.example.com/a.jpg",
            Ech0Api.resolveUrlWithBase("https://example.com", "//cdn.example.com/a.jpg")
        )
    }

    @Test
    fun `absolute url is unchanged`() {
        assertEquals(
            "http://cdn.example.com/a.jpg",
            Ech0Api.resolveUrlWithBase("https://example.com", "http://cdn.example.com/a.jpg")
        )
    }

    @Test
    fun `probe base trims trailing slash`() {
        assertEquals(
            "https://echo.example.com",
            Ech0Api.normalizeProbeBase("  https://echo.example.com/  ")
        )
    }

    @Test(expected = ApiException::class)
    fun `probe base rejects missing scheme`() {
        Ech0Api.normalizeProbeBase("echo.example.com")
    }

    @Test(expected = ApiException::class)
    fun `probe base rejects blank host`() {
        Ech0Api.normalizeProbeBase("https://")
    }

    @Test
    fun `proxy parser accepts host and port`() {
        assertTrue(HttpClients.isValidProxy(""))
        assertTrue(HttpClients.isValidProxy("127.0.0.1:7890"))
        assertTrue(HttpClients.isValidProxy("http://proxy.example.com:8080"))
        assertFalse(HttpClients.isValidProxy("proxy.example.com"))
        assertFalse(HttpClients.isValidProxy("127.0.0.1:99999"))
    }
}
