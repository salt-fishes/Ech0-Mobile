package top.saltfish.ech0.data

/** 随包发布的本地更新记录（离线也能看） */
object AppReleaseNotes {
    const val VERSION = "0.3.0"

    val items = listOf(
        "启动静默同步：先显示缓存，再自动拉取最新时间线/发现/友链内容",
        "新增启动品牌动效与同步提示，更新时间线时保留列表动画",
        "主题色改为色盘自由选取；保留动态取色与 4 种背景",
        "新增 GitHub Release 版本检查：启动静默检查，关于页可手动检查",
        "错误提示统一：页面错误 / 瞬时错误 / 取消类异常分开处理",
        "点赞单向且本地防重复：Ech0 无 unlike 接口，已赞后不可取消；列表与详情计数统一",
        "友链聚合支持 Markdown/媒体/来源 chip；标签筛选与发现页跳转",
        "窄屏/真机适配：顶栏 logo fallback、底部 scrim、设置页背景预览换行"
    )
}
