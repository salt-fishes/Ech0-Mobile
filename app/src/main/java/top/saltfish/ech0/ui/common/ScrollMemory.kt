package top.saltfish.ech0.ui.common

/**
 * 简单记录列表的滚动位置：进入详情再返回时，页面可能被重新组合，
 * 用全局记录把时间线 / 友链恢复到离开前的位置。
 */
object ScrollMemory {
    var timelineIndex: Int = 0
    var timelineOffset: Int = 0
    var hubIndex: Int = 0
    var hubOffset: Int = 0
}
