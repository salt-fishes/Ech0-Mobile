package top.saltfish.ech0.data

import androidx.compose.runtime.mutableStateMapOf

/**
 * 点赞控制器：
 * - Ech0 服务端只有 like 接口（fav_count +1），没有 unlike，点赞是单向的
 * - 本地先记录（乐观 UI），再发一次网络请求；已赞后再次点击由 UI 提示不可取消
 * - 同一帖在请求中忽略重复点击
 * - 请求失败自动回滚本地记录，由 UI 统一提示
 * - 点赞计数在会话内全局共享，列表页与详情页始终一致
 */
object LikeController {
    private val inFlight = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** 会话内统一计数（列表/详情共用） */
    private val counts = mutableStateMapOf<String, Int>()

    /** 本会话内本地调整过的帖子：服务端回包时不用覆盖本地计数，避免 +1 两次 */
    private val locallyAdjusted = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** 用服务端数据校准计数；本会话内用户操作过的帖子保留本地值 */
    fun sync(id: String, serverCount: Int) {
        if (id !in locallyAdjusted || id !in counts) {
            counts[id] = serverCount
        }
    }

    fun count(id: String, fallback: Int): Int = counts[id] ?: fallback

    suspend fun like(id: String, baseCount: Int): Result<Unit> {
        if (!inFlight.add(id)) return Result.success(Unit)
        if (id in Ech0Store.current.likedIds) {
            inFlight.remove(id)
            return Result.success(Unit)
        }
        val before = counts[id] ?: baseCount
        locallyAdjusted.add(id)
        counts[id] = before + 1
        return try {
            Ech0Store.setLiked(id, true)
            Ech0Api.likeEcho(id)
            Result.success(Unit)
        } catch (e: Exception) {
            runCatching { Ech0Store.setLiked(id, false) }
            counts[id] = before
            Result.failure(e)
        } finally {
            inFlight.remove(id)
        }
    }
}
