package com.luread.app.ui.reader

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Pembaca HTML tunggal (misal hasil unduhan `text/html` dari Gutenberg).
 *
 * File dibuka lewat WebView lokal dengan JavaScript dimatikan dan hanya
 * berkas di perangkat — tidak ada permintaan jaringan dari pembaca ini.
 * Progres dipantau dari posisi scroll halaman.
 */
@Composable
fun HtmlReader(
    path: String,
    fontSp: Float,
    onProgress: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val background = MaterialTheme.colorScheme.background

    val webView = remember(path) {
        @SuppressLint("SetJavaScriptEnabled")
        WebView(context).apply {
            settings.javaScriptEnabled = false
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(AndroidColor.TRANSPARENT)
            webViewClient = WebViewClient()
            loadUrl("file://$path")
        }
    }

    DisposableEffect(webView) {
        onDispose { runCatching { webView.destroy() } }
    }

    AndroidView(
        factory = { webView },
        update = { view ->
            view.setBackgroundColor(background.toArgb())
        },
        onRelease = { view -> runCatching { view.destroy() } },
        modifier = modifier.fillMaxSize(),
    )

    LaunchedEffect(webView, fontSp) {
        webView.settings.textZoom = (fontSp / 16f * 100f).toInt().coerceIn(50, 300)
        while (isActive) {
            delay(1_000)
            webView.evaluateJavascript(SCROLL_PROGRESS_JS) { raw ->
                val value = raw?.trim('"')?.toDoubleOrNull()
                if (value != null && value.isFinite()) onProgress(value.toFloat())
            }
        }
    }
}

private const val SCROLL_PROGRESS_JS =
    "(function(){" +
        "var d=document.documentElement,b=document.body||{scrollHeight:0};" +
        "var h=Math.max(b.scrollHeight,d.scrollHeight,1);" +
        "var y=window.scrollY||d.scrollTop||0;" +
        "var vh=window.innerHeight||1;" +
        "return String(Math.min(1,Math.max(0,(y+vh)/h)));})()"
