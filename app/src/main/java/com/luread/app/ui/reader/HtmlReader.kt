package com.luread.app.ui.reader

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File

/**
 * Pembaca HTML tunggal (misal hasil unduhan `text/html` dari Gutenberg).
 *
 * File dibuka lewat WebView lokal dengan JavaScript dimatikan dan hanya
 * berkas di perangkat — tidak ada permintaan jaringan dari pembaca ini.
 * Progres dipantau dari posisi scroll halaman.
 *
 * Latar belakang DIPAKSA putih dan teks hitam (CSS di-suntikkan) karena
 * banyak file HTML Gutenberg punya latar gelap yang membuat teks hitam
 * tidak terbaca.
 */
@Composable
fun HtmlReader(
    path: String,
    fontSp: Float,
    onProgress: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val webView = remember(path) {
        @SuppressLint("SetJavaScriptEnabled")
        WebView(context).apply {
            settings.javaScriptEnabled = false
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(AndroidColor.WHITE)
            webViewClient = WebViewClient()
            val file = File(path)
            val raw = runCatching { file.readText() }.getOrDefault("")
            val html = injectWhiteCss(raw)
            loadDataWithBaseURL("file://${file.parent}/", html, "text/html", "utf-8", null)
        }
    }

    DisposableEffect(webView) {
        onDispose { runCatching { webView.destroy() } }
    }

    AndroidView(
        factory = { webView },
        update = { view ->
            view.setBackgroundColor(AndroidColor.WHITE)
        },
        onRelease = { view -> runCatching { view.destroy() } },
        modifier = modifier
            .fillMaxSize()
            .background(Color.White),
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

/** Suntikkan CSS paksa putih/ hitam ke dokumen HTML. */
internal fun injectWhiteCss(html: String): String {
    val css = """
        <style id="luread-white">
        html, body { background: #FFFFFF !important; color: #000000 !important; }
        body, p, div, span, td, li, blockquote, pre, code, em, strong, i, b,
        h1, h2, h3, h4, h5, h6, a, article, section, main, header, footer,
        figure, figcaption, caption, label, dt, dd, small, sub, sup {
            color: #000000 !important;
            background-color: transparent !important;
        }
        a { color: #1155CC !important; text-decoration: underline; }
        table, th, tr { background: transparent !important; color: #000000 !important; border-color: #CCCCCC !important; }
        hr { border-color: #CCCCCC !important; }
        img { max-width: 100%; height: auto; }
        </style>
    """.trimIndent()
    val lower = html.lowercase()
    val headIdx = lower.indexOf("<head")
    if (headIdx >= 0) {
        val closeIdx = lower.indexOf('>', headIdx)
        if (closeIdx >= 0) {
            return html.substring(0, closeIdx + 1) + css + html.substring(closeIdx + 1)
        }
    }
    val htmlIdx = lower.indexOf("<html")
    if (htmlIdx >= 0) {
        val closeIdx = lower.indexOf('>', htmlIdx)
        if (closeIdx >= 0) {
            return html.substring(0, closeIdx + 1) + "<head>" + css + "</head>" + html.substring(closeIdx + 1)
        }
    }
    return css + html
}

private const val SCROLL_PROGRESS_JS =
    "(function(){" +
        "var d=document.documentElement,b=document.body||{scrollHeight:0};" +
        "var h=Math.max(b.scrollHeight,d.scrollHeight,1);" +
        "var y=window.scrollY||d.scrollTop||0;" +
        "var vh=window.innerHeight||1;" +
        "return String(Math.min(1,Math.max(0,(y+vh)/h)));})()"
