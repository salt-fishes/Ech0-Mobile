package top.saltfish.ech0

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import top.saltfish.ech0.ui.Ech0AppRoot

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentSafely(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingShare.value = extractShare(intent)
        if (intent.action == ACTION_COMPOSE) pendingComposeOpen.value = true
    }

    private fun setContentSafely(intent: Intent?) {
        pendingShare.value = extractShare(intent)
        if (intent?.action == ACTION_COMPOSE) pendingComposeOpen.value = true
        setContent {
            val share by pendingShare
            val openCompose by pendingComposeOpen
            LaunchedEffect(openCompose, share) {
                if (openCompose || share.text.isNotBlank() || share.images.isNotEmpty()) {
                    composerRequest.value = share
                    pendingComposeOpen.value = false
                    pendingShare.value = SharePayload()
                }
            }
            Ech0AppRoot(sharedInitial = composerRequest.value)
        }
    }

    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    private fun extractShare(intent: Intent?): SharePayload {
        if (intent == null) return SharePayload()
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                val images = if (intent.type?.startsWith("image/") == true) {
                    listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM) as? android.net.Uri)
                } else emptyList()
                SharePayload(text, images)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val images = intent.getParcelableArrayListExtra<android.os.Parcelable>(Intent.EXTRA_STREAM)
                    ?.filterIsInstance<android.net.Uri>().orEmpty()
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                SharePayload(text, images)
            }
            else -> SharePayload()
        }
    }

    companion object {
        const val ACTION_COMPOSE = "top.saltfish.ech0.action.COMPOSE"

        // 简单进程内共享状态：分享/快捷方式 → 编辑器
        val composerRequest = mutableStateOf(SharePayload())
        val pendingShare = mutableStateOf(SharePayload())
        val pendingComposeOpen = mutableStateOf(false)
    }
}

data class SharePayload(val text: String = "", val images: List<android.net.Uri> = emptyList())
