@file:OptIn(ExperimentalMaterial3Api::class)

package top.saltfish.ech0.ui.me

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.saltfish.ech0.R
import top.saltfish.ech0.data.AppRelease
import top.saltfish.ech0.data.DefaultGitHubRepo
import top.saltfish.ech0.data.UpdateChecker
import top.saltfish.ech0.data.userMessage
import top.saltfish.ech0.ui.common.MarkdownText

/** GitHub Release 发现新版本时的底部卡片（启动静默检查 / 设置页手动检查共用） */
@Composable
fun AppReleaseSheet(
    release: AppRelease,
    currentVersion: String,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uriHandler = LocalUriHandler.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_tree_logo),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "发现新版本 ${release.versionName}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "当前 v$currentVersion" +
                            (release.publishedAt?.take(10)?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text("更新内容", style = MaterialTheme.typography.labelLarge)
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                MarkdownText(
                    markdown = release.notes.ifBlank { "查看 GitHub Release 了解详情。" },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                    )
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        val url = release.apkUrl?.takeIf { it.isNotBlank() } ?: release.htmlUrl
                        if (url.isNotBlank()) {
                            runCatching { uriHandler.openUri(url) }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (release.apkUrl != null) "前往下载 APK" else "查看 Release")
                }
                TextButton(
                    onClick = {
                        if (release.htmlUrl.isNotBlank()) {
                            runCatching { uriHandler.openUri(release.htmlUrl) }
                        }
                    }
                ) {
                    Text("GitHub")
                }
                TextButton(onClick = onDismiss) { Text("稍后") }
            }
        }
    }
}

@Composable
fun AppChangelogDialog(onDismiss: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var releases by remember { mutableStateOf<List<AppRelease>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    fun load() {
        if (loading && releases.isNotEmpty()) return
        loading = true
        error = null
        scope.launch {
            UpdateChecker.releases(DefaultGitHubRepo, limit = 8).fold(
                onSuccess = { releases = it },
                onFailure = { error = it.userMessage() }
            )
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更新记录") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when {
                    loading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            "正在从 GitHub 获取更新记录…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    error != null -> {
                        Text(
                            "获取失败：$error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            "请检查网络或代理后重试。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { load() }) { Text("重试") }
                    }

                    releases.isEmpty() -> Text(
                        "GitHub 上还没有 Release 记录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    else -> releases.forEachIndexed { index, release ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "v${release.versionName}" +
                                    (release.publishedAt?.take(10)?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.titleSmall
                            )
                            MarkdownText(
                                markdown = release.notes.ifBlank { "（无更新说明）" },
                                style = MaterialTheme.typography.bodySmall
                            )
                            TextButton(
                                onClick = {
                                    if (release.htmlUrl.isNotBlank()) {
                                        runCatching { uriHandler.openUri(release.htmlUrl) }
                                    }
                                },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                            ) {
                                Text("在 GitHub 查看", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (index != releases.lastIndex) HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
    )
}
