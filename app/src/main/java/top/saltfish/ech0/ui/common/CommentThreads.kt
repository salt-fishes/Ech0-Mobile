package top.saltfish.ech0.ui.common

import top.saltfish.ech0.data.PublicComment

/** 一级评论 + 它下面的全部回复（含回复的回复，统一挂到根评论下） */
data class CommentThread(
    val root: PublicComment,
    val replies: List<PublicComment>
)

private fun rootOf(comment: PublicComment, byId: Map<String, PublicComment>): PublicComment {
    var current = comment
    val seen = mutableSetOf<String>()
    while (seen.add(current.id)) {
        current = current.parent_id?.let { byId[it] } ?: break
    }
    return current
}

/**
 * 按 parent_id 把评论整理成两层：根评论保持服务端顺序，回复按时间正序挂在根下。
 * 找不到父评论的回复当成根评论，避免丢失。
 */
fun threadComments(comments: List<PublicComment>): List<CommentThread> {
    if (comments.isEmpty()) return emptyList()
    val byId = comments.associateBy { it.id }
    val rootIds = comments.associate { it.id to rootOf(it, byId).id }
    val replies = mutableMapOf<String, MutableList<PublicComment>>()
    comments.forEach { comment ->
        val rootId = rootIds[comment.id] ?: comment.id
        if (rootId != comment.id) {
            replies.getOrPut(rootId) { mutableListOf() }.add(comment)
        }
    }
    return comments
        .filter { rootIds[it.id] == it.id }
        .map { root ->
            CommentThread(
                root = root,
                replies = replies[root.id].orEmpty().sortedBy { it.created_at }
            )
        }
}
