package com.example.web

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.view.View
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.components.SimulationNoticePill
import com.example.ui.theme.CrimsonRecord
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.SapphireBlue
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.net.URLEncoder

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebAppsScreen(
    appId: String,
    title: String,
    initialUrl: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var currentUrl by remember(initialUrl) { mutableStateOf(initialUrl) }
    var urlInput by remember(initialUrl) { mutableStateOf(initialUrl) }
    var pageTitle by remember(title) { mutableStateOf(title) }
    var progress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var webError by remember { mutableStateOf<String?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var customFullscreenView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                // Security hardening
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return false
                    val scheme = uri.scheme?.lowercase() ?: return true
                    return when (scheme) {
                        "https" -> false
                        "http" -> {
                            // Upgrade cleartext http requests to https cleanly
                            val upgraded = uri.buildUpon().scheme("https").build().toString()
                            view?.loadUrl(upgraded)
                            true
                        }
                        "tel", "mailto", "geo" -> {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            }
                            true
                        }
                        else -> true // Block arbitrary/unsafe schemes
                    }
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    isLoading = true
                    webError = null
                    if (url != null) {
                        currentUrl = url
                        urlInput = url
                    }
                    canGoBack = view?.canGoBack() == true
                    canGoForward = view?.canGoForward() == true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    isLoading = false
                    if (url != null) {
                        currentUrl = url
                        urlInput = url
                    }
                    view?.title?.takeIf { it.isNotBlank() }?.let { pageTitle = it }
                    canGoBack = view?.canGoBack() == true
                    canGoForward = view?.canGoForward() == true
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        isLoading = false
                        webError = "Unable to load page (${error?.description ?: "Network unavailable"})."
                    }
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    // Never proceed on untrusted SSL certificates
                    handler?.cancel()
                    isLoading = false
                    webError = "Secure HTTPS connection rejected due to invalid SSL certificate."
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress
                }

                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    customFullscreenView = view
                    customViewCallback = callback
                }

                override fun onHideCustomView() {
                    customFullscreenView = null
                    customViewCallback?.onCustomViewHidden()
                    customViewCallback = null
                }
            }

            loadUrl(initialUrl)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    BackHandler {
        when {
            customFullscreenView != null -> {
                customFullscreenView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
            }
            webView.canGoBack() -> webView.goBack()
            else -> onBack()
        }
    }

    fun navigateToInput(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        val target = when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> "https://" + trimmed.removePrefix("http://")
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> "https://www.google.com/search?q=${URLEncoder.encode(trimmed, "UTF-8")}"
        }
        webView.loadUrl(target)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Safari / Web App Address Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianElevated)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("web_close_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Web App", tint = TextPrimary)
                    }

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "HTTPS",
                                tint = if (currentUrl.startsWith("https://")) EmeraldLive else CrimsonRecord,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { navigateToInput(urlInput) }),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("web_url_input"),
                        shape = RoundedCornerShape(16.dp)
                    )

                    IconButton(
                        onClick = { webView.reload() },
                        modifier = Modifier.testTag("web_reload_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = SapphireBlue)
                    }
                }

                // Quick Bookmarks Strip in Browser / Google modes
                if (appId == "browser" || appId == "google") {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "Google" to "https://www.google.com",
                            "YouTube" to "https://m.youtube.com",
                            "TikTok" to "https://www.tiktok.com",
                            "Gmail" to "https://mail.google.com",
                            "Maps" to "https://www.google.com/maps",
                            "Wikipedia" to "https://en.m.wikipedia.org"
                        ).forEach { (label, url) ->
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, GlassBorder, CircleShape)
                                    .clickable { webView.loadUrl(url) }
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(label, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                            }
                        }
                    }
                }

                if (isLoading && progress in 1..99) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = SapphireBlue
                    )
                }
            }

            webError?.let { err ->
                Box(modifier = Modifier.padding(12.dp)) {
                    SimulationNoticePill(text = err, accentColor = CrimsonRecord)
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { webView },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Bottom Navigation Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianElevated)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (webView.canGoBack()) webView.goBack() },
                    enabled = canGoBack,
                    modifier = Modifier.testTag("web_back_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (canGoBack) TextPrimary else TextSecondary.copy(alpha = 0.35f)
                    )
                }
                IconButton(
                    onClick = { if (webView.canGoForward()) webView.goForward() },
                    enabled = canGoForward
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Forward",
                        tint = if (canGoForward) TextPrimary else TextSecondary.copy(alpha = 0.35f)
                    )
                }
                Text(
                    text = pageTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                IconButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, currentUrl)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Link"))
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share URL", tint = SapphireBlue)
                }
            }
        }

        // Fullscreen HTML5 Video Container (for YouTube / TikTok fullscreen playback)
        customFullscreenView?.let { fsView ->
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        addView(fsView)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
