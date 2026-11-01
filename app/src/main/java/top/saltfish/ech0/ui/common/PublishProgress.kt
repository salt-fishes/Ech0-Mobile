package top.saltfish.ech0.ui.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 顶部发布进度：编辑器在发布时先隐藏，进度统一在应用顶部展示。 */
data class PublishProgressState(
    val running: Boolean = false,
    val finished: Boolean = false,
    val success: Boolean = true,
    val progress: Float = 0f,
    val label: String = "",
    val detail: String? = null,
    val indeterminate: Boolean = false
)

object PublishProgress {

    private val _state = MutableStateFlow(PublishProgressState())
    val state: StateFlow<PublishProgressState> = _state.asStateFlow()

    private var total = 0
    private val imageProgress = mutableMapOf<Int, Float>()

    @Synchronized
    fun beginImages(totalImages: Int) {
        total = totalImages
        imageProgress.clear()
        _state.value = PublishProgressState(
            running = true,
            progress = 0f,
            label = if (totalImages == 0) "正在发布…" else "正在上传图片 0/$totalImages",
            indeterminate = totalImages == 0
        )
    }

    @Synchronized
    fun imageProgress(index: Int, progress: Float) {
        if (total <= 0 || !_state.value.running) return
        val previous = imageProgress[index] ?: 0f
        imageProgress[index] = maxOf(previous, progress.coerceIn(0f, 1f))
        val sum = imageProgress.values.sumOf { it.toDouble() }.toFloat()
        val done = imageProgress.values.count { it >= 0.999f }
        _state.value = PublishProgressState(
            running = true,
            progress = (sum / total).coerceIn(0f, 1f),
            label = "正在上传图片 $done/$total",
            indeterminate = false
        )
    }

    @Synchronized
    fun publishing(label: String = "正在发布…") {
        _state.value = PublishProgressState(
            running = true,
            progress = if (total == 0) 0f else 1f,
            label = label,
            indeterminate = total == 0
        )
    }

    @Synchronized
    fun finish(success: Boolean, detail: String? = null) {
        _state.value = PublishProgressState(
            running = false,
            finished = true,
            success = success,
            progress = if (success) 1f else _state.value.progress,
            label = if (success) "发布完成" else "发布未完成",
            detail = detail,
            indeterminate = false
        )
    }

    @Synchronized
    fun dismiss() {
        total = 0
        imageProgress.clear()
        _state.value = PublishProgressState()
    }
}
