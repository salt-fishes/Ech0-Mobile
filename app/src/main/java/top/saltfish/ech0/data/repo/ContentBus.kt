package top.saltfish.ech0.data.repo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import top.saltfish.ech0.data.Echo

/**
 * 内容变更总线：
 * - feedRevision：发布/编辑/删除后，首页/友链本地源/发现页观察并刷新
 * - detailRevision：单条详情相关变更（点赞/评论）预留
 *
 * 当前只做失效通知，不做复杂的局部 patch。
 */
object ContentBus {

    var feedRevision by mutableIntStateOf(0)
        private set

    var detailRevision by mutableIntStateOf(0)
        private set

    fun notifyPublished(echo: Echo? = null) {
        echo?.let { PostStore.upsert(it) }
        PostStore.invalidateQueries()
        feedRevision++
    }

    fun notifyUpdated(echo: Echo) {
        PostStore.upsert(echo)
        PostStore.invalidateQueries()
        feedRevision++
        detailRevision++
    }

    fun notifyDeleted(id: String) {
        PostStore.remove(id)
        PostStore.invalidateQueries()
        feedRevision++
        detailRevision++
    }

    fun notifyLikeChanged() {
        detailRevision++
    }

    fun notifyCommentChanged() {
        detailRevision++
    }
}
