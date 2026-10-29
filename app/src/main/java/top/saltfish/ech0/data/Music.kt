package top.saltfish.ech0.data

/** 音乐平台解析结果，字段与网页版 parseMusicURL 对齐 */
data class MusicLink(
    val server: String,
    val type: String,
    val id: String
)

data class MusicTrack(
    val name: String,
    val artist: String,
    val url: String,
    val cover: String,
    val lrc: String
)

object MusicLinks {

    fun parse(raw: String): MusicLink? {
        val url = raw.trim()
        if (url.isEmpty()) return null
        val lower = url.lowercase()

        if (Regex("""^https://([a-z0-9-]+\.)*music\.163\.com""").containsMatchIn(lower)) {
            val id = Regex("""[?&]id=(\d+)""").find(url)?.groupValues?.get(1) ?: return null
            val type = when {
                Regex("""(/|#/|/m/)song""").containsMatchIn(url) -> "song"
                Regex("""(/|#/|/m/)playlist""").containsMatchIn(url) -> "playlist"
                else -> return null
            }
            return MusicLink("netease", type, id)
        }

        if (Regex("""^https://([a-z0-9-]+\.)*qq\.com""").containsMatchIn(lower)) {
            Regex("""songDetail/([a-zA-Z0-9]+)""").find(url)?.let {
                return MusicLink("tencent", "song", it.groupValues[1])
            }
            Regex("""[?&]songid=(\d+)""").find(url)?.let {
                return MusicLink("tencent", "song", it.groupValues[1])
            }
            Regex("""/playlist/(\d+)""", RegexOption.IGNORE_CASE).find(url)?.let {
                return MusicLink("tencent", "playlist", it.groupValues[1])
            }
            return null
        }

        if (Regex("""^https://music\.apple\.com""").containsMatchIn(lower)) {
            val match = Regex("""/(song|album)/[^/]+/(\d+)""").find(url) ?: return null
            return MusicLink("apple", match.groupValues[1], match.groupValues[2])
        }
        return null
    }
}
