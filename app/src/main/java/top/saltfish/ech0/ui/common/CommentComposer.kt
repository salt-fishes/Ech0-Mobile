package top.saltfish.ech0.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.saltfish.ech0.data.Ech0Store

data class CommentIdentity(
    val nickname: String,
    val email: String,
    val website: String
)

/**
 * 评论输入区：默认身份开启时自动带入昵称 / 邮箱 / 网站；
 * 关闭后每次展开三个字段，也可在输入区里临时修改。
 */
@Composable
fun CommentComposer(
    hint: String?,
    sending: Boolean,
    replyToName: String?,
    resetKey: Int,
    onCancelReply: () -> Unit,
    onSubmit: (CommentIdentity, String) -> Unit,
    onDraftChange: ((String) -> Unit)? = null,
    initialDraft: String = "",
    modifier: Modifier = Modifier
) {
    val store = Ech0Store.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var useDefault by remember { mutableStateOf(store.commentUseDefault) }
    var nickname by remember {
        mutableStateOf(store.commentNickname.ifBlank { store.username.ifBlank { "游客" } })
    }
    var email by remember { mutableStateOf(store.commentEmail) }
    var website by remember { mutableStateOf(store.commentWebsite) }
    var content by remember { mutableStateOf(initialDraft) }
    var showIdentityEditor by remember { mutableStateOf(false) }
    var pendingSubmit by remember { mutableStateOf<Pair<CommentIdentity, String>?>(null) }

    // 父级异步恢复草稿时只填充一次，避免每次输入都把光标重置到末尾
    LaunchedEffect(initialDraft) {
        if (content.isBlank() && initialDraft.isNotBlank()) {
            content = initialDraft
        }
    }

    // 评论身份按实例 / 账号隔离；旧版本全局身份只迁移到当前实例
    LaunchedEffect(store.baseUrl, store.username) {
        val stored = Ech0Store.currentCommentIdentity() ?: return@LaunchedEffect
        useDefault = stored.useDefault
        nickname = stored.nickname.ifBlank { store.username.ifBlank { "游客" } }
        email = stored.email
        website = stored.website
    }

    LaunchedEffect(resetKey) {
        if (resetKey > 0) {
            content = ""
            onDraftChange?.invoke("")
        }
    }

    // 点评论开始回复：自动聚焦并弹出输入法，不再显示「回复 xx（取消）」那一行
    LaunchedEffect(replyToName) {
        if (replyToName != null) {
            delay(120)
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }

    val showFields = !useDefault || email.isBlank()
    val identity = CommentIdentity(
        nickname = nickname.ifBlank { store.username.ifBlank { "游客" } },
        email = email.trim(),
        website = website.trim()
    )

    Column(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        hint?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
            )
        }
        if (replyToName != null) {
            Text(
                "回复 $replyToName",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
            )
        }
        if (showFields) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { if (it.length <= 60) nickname = it },
                    placeholder = { Text("昵称", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { if (it.length <= 120) email = it },
                    placeholder = { Text("邮箱（必填，不公开）", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1.4f)
                )
            }
            OutlinedTextField(
                value = website,
                onValueChange = { if (it.length <= 200) website = it },
                placeholder = { Text("网站（可选）", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        } else {
            Row(
                Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "以 ${identity.nickname} 评论",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = { showIdentityEditor = true },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Text("修改身份", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        OutlinedTextField(
            value = content,
            onValueChange = {
                if (it.length <= 500) {
                    content = it
                    onDraftChange?.invoke(it)
                }
            },
            placeholder = {
                Text(if (replyToName != null) "回复 $replyToName…" else "说两句…")
            },
            maxLines = 4,
            shape = RoundedCornerShape(24.dp),
            trailingIcon = {
                IconButton(
                    onClick = {
                        if (content.isNotBlank() && !sending) {
                            if (Ech0Store.current.commentConfirm) {
                                pendingSubmit = identity to content.trim()
                            } else {
                                if (showFields) {
                                    scope.launch {
                                        Ech0Store.setCommentIdentity(
                                            nickname = identity.nickname,
                                            email = identity.email,
                                            website = identity.website,
                                            useDefault = useDefault
                                        )
                                    }
                                }
                                onSubmit(identity, content.trim())
                            }
                        }
                    },
                    enabled = !sending && content.isNotBlank()
                ) {
                    if (sending) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Filled.Send,
                            contentDescription = "发送",
                            tint = if (content.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    // 点界面其他位置取消回复；已经写了评论草稿时保留回复目标
                    if (!focusState.isFocused && replyToName != null && content.isBlank() && !sending) {
                        onCancelReply()
                    }
                }
        )
    }

    if (pendingSubmit != null) {
        AlertDialog(
            onDismissRequest = { pendingSubmit = null },
            title = { Text("发送评论？") },
            text = { Text(pendingSubmit!!.second) },
            confirmButton = {
                TextButton(onClick = {
                    val (pendingIdentity, pendingContent) = pendingSubmit!!
                    pendingSubmit = null
                    if (showFields) {
                        scope.launch {
                            Ech0Store.setCommentIdentity(
                                nickname = pendingIdentity.nickname,
                                email = pendingIdentity.email,
                                website = pendingIdentity.website,
                                useDefault = useDefault
                            )
                        }
                    }
                    onSubmit(pendingIdentity, pendingContent)
                }) { Text("发送") }
            },
            dismissButton = {
                TextButton(onClick = { pendingSubmit = null }) { Text("取消") }
            }
        )
    }

    if (showIdentityEditor) {
        var editNickname by remember { mutableStateOf(identity.nickname) }
        var editEmail by remember { mutableStateOf(identity.email) }
        var editWebsite by remember { mutableStateOf(identity.website) }
        var saveDefault by remember { mutableStateOf(useDefault) }
        AlertDialog(
            onDismissRequest = { showIdentityEditor = false },
            title = { Text("评论身份") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editNickname,
                        onValueChange = { editNickname = it },
                        label = { Text("昵称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editEmail,
                        onValueChange = { editEmail = it },
                        label = { Text("邮箱（必填，不公开）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editWebsite,
                        onValueChange = { editWebsite = it },
                        label = { Text("网站（可选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "保存为默认身份",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = saveDefault, onCheckedChange = { saveDefault = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    nickname = editNickname
                    email = editEmail
                    website = editWebsite
                    useDefault = saveDefault
                    scope.launch {
                        Ech0Store.setCommentIdentity(
                            nickname = editNickname.trim(),
                            email = editEmail.trim(),
                            website = editWebsite.trim(),
                            useDefault = saveDefault
                        )
                    }
                    showIdentityEditor = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showIdentityEditor = false }) { Text("取消") }
            }
        )
    }
}
