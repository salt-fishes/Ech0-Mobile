package top.saltfish.ech0.data

import androidx.compose.runtime.mutableStateMapOf

/**
 * 点赞控制器：
 * - Ech0 服务端只有 like 接口（fav_count +1），没有 unlike，点赞是单向的
 * - 本地先记录（乐观 UI），再发一次网络请求；已赞后再次点击由 UI 提示不可取消
 * - 同一帖在请求中忽略重复点击
 * - 请求失败自动回滚本地记录，由 UI 统一提示
 * - 点赞计数在会话内全局共享，列表页与详情页始终一致
 * - 友链远端帖子用 remote:<base>:<id> 作为 key，和本站帖子共用一套计数与已赞状态
 */
object LikeController {
    private val inFlight = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** 会话内统一计数（列表/详情共用） */
    private val counts = mutableStateMapOf<String, Int>()

    /** 本会话内本地调整过的帖子：服务端回包时不用覆盖本地计数，避免 +1 两次 */
    private val locallyAdjusted = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** 友链远端帖子的本地 key：持久化在 likedIds 里，重启后仍能显示已赞 */
    fun remoteKey(remoteBase: String, id: String): String =
        "remote:${remoteBase.trimEnd('/')}:$id"

    /** 用服务端数据校准计数；本会话内用户操作过的帖子保留本地值 */
    fun sync(id: String, serverCount: Int) {
        if (id !in locallyAdjusted || id !in counts) {
            counts[id] = serverCount
        }
    }

    fun count(id: String, fallback: Int): Int = counts[id] ?: fallback

    suspend fun like(id: String, baseCount: Int): Result<Unit> =
        likeKey(id, baseCount) { Ech0Api.likeEcho(id) }

    suspend fun likeRemote(remoteBase: String, id: String, baseCount: Int): Result<Unit> =
        likeKey(remoteKey(remoteBase, id), baseCount) {
            Ech0Api.remoteLikeEcho(remoteBase, id)
        }

    private suspend fun likeKey(
        key: String,
        baseCount: Int,
        request: suspend () -> Unit
    ): Result<Unit> {
        if (!inFlight.add(key)) return Result.success(Unit)
        if (key in Ech0Store.current.likedIds) {
            inFlight.remove(key)
            return Result.success(Unit)
        }
        val before = counts[key] ?: baseCount
        locallyAdjusted.add(key)
        counts[key] = before + 1
        return try {
            Ech0Store.setLiked(key, true)
            request()
            Result.success(Unit)
        } catch (e: Exception) {
            runCatching { Ech0Store.setLiked(key, false) }
            counts[key] = before
            Result.failure(e)
        } finally {
            inFlight.remove(key)
        }
    }
}
