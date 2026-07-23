package com.sublingo.app.ui.download

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sublingo.app.data.media.YoutubeLoginCookiePolicy
import kotlinx.coroutines.delay

private const val YOUTUBE_LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fwww.youtube.com%2F"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YoutubeLoginDialog(
    targetVideoUrl: String,
    onAuthenticated: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var pageLoading by remember { mutableStateOf(true) }
    var completed by remember { mutableStateOf(false) }
    var currentHost by remember { mutableStateOf("accounts.google.com") }
    val cookieManager = remember {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
        }
    }
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportMultipleWindows(false)
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    pageLoading = true
                    currentHost = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
                }

                override fun onPageFinished(view: WebView, url: String) {
                    pageLoading = false
                    currentHost = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val scheme = request.url.scheme.orEmpty()
                    return scheme != "https" && scheme != "http"
                }
            }
            loadUrl(YOUTUBE_LOGIN_URL)
        }
    }

    fun authenticatedCookie(): String? {
        cookieManager.flush()
        val cookie = YoutubeLoginCookiePolicy.normalize(
            listOf(
                cookieManager.getCookie("https://www.youtube.com/"),
                cookieManager.getCookie("https://m.youtube.com/"),
                cookieManager.getCookie(targetVideoUrl),
            ),
        )
        return cookie.takeIf(YoutubeLoginCookiePolicy::hasAuthenticatedSession)
    }

    LaunchedEffect(webView) {
        while (!completed) {
            authenticatedCookie()?.let { cookie ->
                completed = true
                onAuthenticated(cookie)
            }
            delay(750)
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.webChromeClient = null
            webView.destroy()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
        }
    }

    BackHandler {
        if (webView.canGoBack()) webView.goBack() else onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = true,
        ),
    ) {
        Column(Modifier.fillMaxSize().background(Color(0xFFFDFAF0))) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(onClick = onDismiss, color = Color(0xFFE9E1D2), shape = MaterialTheme.shapes.extraLarge) {
                    Text("取消", Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("登录 YouTube", style = MaterialTheme.typography.titleMedium)
                    Text(
                        currentHost.ifBlank { "安全登录页" },
                        color = Color(0xFF747688),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (pageLoading) CircularProgressIndicator(Modifier.height(24.dp), strokeWidth = 2.dp)
            }
            Text(
                "请在下方完成登录。App 只读取 YouTube 下载所需的会话 Cookie，并使用 Android Keystore 加密保存在本机；检测到登录成功后会自动关闭并重试下载。",
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                color = Color(0xFF5F6170),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(4.dp))
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}
