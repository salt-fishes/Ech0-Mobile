package top.saltfish.ech0.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicLinksTest {

    @Test
    fun parsesStandardNeteaseSongLink() {
        assertEquals(
            MusicLink("netease", "song", "27946926"),
            MusicLinks.parse("https://music.163.com/#/song?id=27946926")
        )
    }

    @Test
    fun parsesMobileNeteaseSongLink() {
        assertEquals(
            MusicLink("netease", "song", "1807799505"),
            MusicLinks.parse(
                "https://y.music.163.com/m/song?id=1807799505&uct2=abc&app_version=9.6.22"
            )
        )
    }

    @Test
    fun recognizesNeteaseShortLink() {
        assertTrue(MusicLinks.isKnownShortLink("https://163cn.tv/blc0Tuvx"))
        assertFalse(MusicLinks.isKnownShortLink("https://music.163.com/#/song?id=27946926"))
        assertFalse(MusicLinks.isKnownShortLink("https://example.com/163cn.tv"))
    }

    @Test
    fun shortLinkNeedsNetworkResolutionBeforeParsing() {
        assertNull(MusicLinks.parse("https://163cn.tv/blc0Tuvx"))
    }
}
