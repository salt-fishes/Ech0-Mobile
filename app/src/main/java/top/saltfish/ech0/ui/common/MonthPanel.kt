package top.saltfish.ech0.ui.common

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

fun monthOf(epochSeconds: Long): YearMonth? =
    if (epochSeconds <= 0) null
    else YearMonth.from(Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()))

fun monthsOf(timestamps: List<Long>): List<YearMonth> =
    timestamps.mapNotNull(::monthOf).distinct().sortedDescending()

/** 最近 N 个月（含当前月），用于日历侧栏始终能快速跳转 */
fun recentMonths(count: Int = 18): List<YearMonth> {
    val now = YearMonth.now()
    return (0 until count).map { now.minusMonths(it.toLong()) }.sortedDescending()
}

/** 右侧月份抽屉：按年份分组，点月份快速跳到该月第一条内容 */
@Composable
fun MonthSidePanel(
    months: List<YearMonth>,
    selected: YearMonth? = null,
    onPick: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
    onLoadMore: (() -> Unit)? = null,
    loadingMore: Boolean = false,
    endReached: Boolean = false
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.24f))
                    .clickable(onClick = onDismiss)
            )
            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(96.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxHeight()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 12.dp, end = 2.dp, top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "月份",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.padding(0.dp)) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "关闭",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (months.isEmpty()) {
                        Text(
                            "暂无内容",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                        return@Column
                    }
                    LazyColumn(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            top = 4.dp, bottom = 16.dp
                        )
                    ) {
                        months.groupBy { it.year }.forEach { (year, list) ->
                            item(key = "year-$year") {
                                Text(
                                    "$year 年",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                )
                            }
                            items(list, key = { "month-$year-${it.monthValue}" }) { month ->
                                val isSelected = month == selected
                                val reduceMotion = LocalReduceMotion.current
                                val corner by animateDpAsState(
                                    targetValue = if (isSelected) 24.dp else 8.dp,
                                    animationSpec = if (reduceMotion) snap()
                                    else MaterialTheme.motionScheme.fastSpatialSpec(),
                                    label = "monthShape"
                                )
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                        .clip(RoundedCornerShape(corner))
                                        .background(
                                            if (isSelected) {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            } else {
                                                Color.Transparent
                                            }
                                        )
                                        .clickable { onPick(month) }
                                        .padding(vertical = 5.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "${month.monthValue}",
                                        fontSize = 28.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Text(
                                        "月",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                        if (onLoadMore != null && !endReached) {
                            item(key = "load-more-months") {
                                TextButton(
                                    onClick = onLoadMore,
                                    enabled = !loadingMore,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                                ) {
                                    if (loadingMore) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(
                                        "加载更早",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
