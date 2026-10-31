package top.saltfish.ech0.ui.common

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import top.saltfish.ech0.data.Ech0Store
import java.net.URI
import androidx.compose.ui.unit.dp

/** 应用内浏览器：用于「链接打开方式 = 应用内」 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(url: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val title = remember(url) {
        runCatching { URI(url).host ?: url }.getOrDefault(url)
    }
    val webView = remember(url) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture =
                !Ech0Store.current.autoPlayVideo
            webViewClient = WebViewClient()
            loadUrl(url)
        }
    }
    DisposableEffect(webView) {
        onDispose {
            runCatching {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.destroy()
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        OverlayTopBar(title, onClose)
        Box(Modifier.fillMaxSize().padding(ContentPadding)) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
        }
        Text(
            "应用内浏览器",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = ContentPadding, vertical = 4.dp)
        )
    }
}
