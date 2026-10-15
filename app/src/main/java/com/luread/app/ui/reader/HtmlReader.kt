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

/** Lebar selokan antar kolom (halaman) pada CSS multi-column. */
internal const val HTML_COLUMN_GAP_PX = 16

/**
 * Pembaca HTML tunggal (misal hasil unduhan `text/html` dari Gutenberg).
 *
 * LUREAD-FIX: halaman HTML dipaginasi horizontal dengan CSS multi-column
 * (`column-width: 100vw`) sehingga navigasi hanya geser kiri/kanan —
 * gulir vertikal dimatikan lewat `overflow: hidden`. Script dari dokumen
 * dibuang ([sanitizeReaderHtml]) sebelum dimuat sehingga hanya CSS
 * buatan Luread yang berjalan; jaringan diblokir penuh oleh WebView.
 */
@Composable
fun HtmlReader(
    path: String,
    fontSp: Float,
    onProgress: (Float) -> Unit,
    modifier: Modifier = Modifier,
    justify: Boolean = false,
    fontFamily: String = "default",
    nav: ReaderNav? = null,
) {
    val context = LocalContext.current

    val webView = remember(path, justify, fontFamily) {
        @SuppressLint("SetJavaScriptEnabled")
        WebView(context).apply {
            // Script dokumen sudah dibuang; JS hanya dipakai untuk
            // menghitung progres halaman milik pembaca sendiri.
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.blockNetworkLoads = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
            setBackgroundColor(AndroidColor.WHITE)
            webViewClient = WebViewClient()
            val file = File(path)
            val raw = runCatching { file.readText() }.getOrDefault("")
            val html = sanitizeReaderHtml(raw, justify = justify, fontFamily = fontFamily)
            loadDataWithBaseURL("file://${file.parent}/", html, "text/html", "utf-8", null)
        }
    }

    DisposableEffect(webView) {
        onDispose { runCatching { webView.destroy() } }
    }

    // LUREAD-FEAT: tombol volume / tap zone maju-mundur per halaman.
    DisposableEffect(nav, webView) {
        nav?.register { delta ->
            val step = webView.width + HTML_COLUMN_GAP_PX
            if (step > HTML_COLUMN_GAP_PX) webView.scrollBy(delta * step, 0)
        }
        onDispose { nav?.register(null) }
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
            delay(700)
            webView.evaluateJavascript(SCROLL_PROGRESS_JS) { raw ->
                val value = raw?.trim('"')?.toDoubleOrNull()
                if (value != null && value.isFinite()) onProgress(value.toFloat())
            }
        }
    }
}

/**
 * Suntikkan CSS halaman horizontal + paksa putih/hitam, buang script dokumen.
 *
 * - `column-width: 100vw` memecah dokumen jadi kolom selebar layar;
 * - `overflow: hidden` pada html/body menutup gulir vertikal;
 * - tag `<script>` dibuang agar dokumen luar tidak mengeksekusi apa pun.
 *
 * @param justifyaktifkan `text-align: justify`.
 * @param fontFamily nama keluarga huruf (`default`/`serif`/`sans`/`monospace`).
 */
internal fun sanitizeReaderHtml(
    html: String,
    justify: Boolean = false,
    fontFamily: String = "default",
): String {
    val stripped = stripScripts(html)
    val css = buildReaderCss(justify = justify, fontFamily = fontFamily)
    val lower = stripped.lowercase()
    val headIdx = lower.indexOf("<head")
    if (headIdx >= 0) {
        val closeIdx = lower.indexOf('>', headIdx)
        if (closeIdx >= 0) {
            return stripped.substring(0, closeIdx + 1) + css + stripped.substring(closeIdx + 1)
        }
    }
    val htmlIdx = lower.indexOf("<html")
    if (htmlIdx >= 0) {
        val closeIdx = lower.indexOf('>', htmlIdx)
        if (closeIdx >= 0) {
            return stripped.substring(0, closeIdx + 1) + "<head>" + css + "</head>" + stripped.substring(closeIdx + 1)
        }
    }
    return css + stripped
}

/** Buang tag `<script>` (buka/tutup, termasuk inline) dari dokumen. */
internal fun stripScripts(html: String): String {
    val withoutBlocks = SCRIPT_BLOCK.replace(html, "")
    return SCRIPT_TAG.replace(withoutBlocks, "")
}

private val SCRIPT_BLOCK =
    Regex("(?is)<script\\b[^>]*>.*?</script\\s*>")
private val SCRIPT_TAG =
    Regex("(?is)<script\\b[^>]*/?>")

/** CSS baca horizontal Luread. */
internal fun buildReaderCss(justify: Boolean, fontFamily: String): String {
    val family = when (fontFamily) {
        "serif" -> "serif"
        "sans" -> "sans-serif"
        "monospace" -> "monospace"
        "cursive" -> "cursive"
        "fantasy" -> "fantasy"
        else -> "serif"
    }
    val alignment = if (justify) "justify" else "left"
    return """
        <style id="luread-page">
        html, body {
            background: #FFFFFF !important;
            color: #000000 !important;
            margin: 0 !important;
            padding: 0 !important;
            height: 100% !important;
            overflow: hidden !important;
            font-family: $family !important;
            text-align: $alignment !important;
        }
        html {
            column-width: 100vw !important;
            column-gap: ${HTML_COLUMN_GAP_PX}px !important;
            column-fill: auto !important;
            -webkit-column-width: 100vw !important;
            -webkit-column-gap: ${HTML_COLUMN_GAP_PX}px !important;
            -webkit-column-fill: auto !important;
            overflow-x: auto !important;
            overflow-y: hidden !important;
        }
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
}

/** Suntikkan CSS paksa putih/hitam ke dokumen HTML (kompatibel lama). */
internal fun injectWhiteCss(html: String): String = sanitizeReaderHtml(html)

private const val SCROLL_PROGRESS_JS =
    "(function(){" +
        "var d=document.documentElement;" +
        "var w=Math.max(d.scrollWidth,document.body?document.body.scrollWidth:1,1);" +
        "var x=window.scrollX||d.scrollLeft||0;" +
        "var vw=window.innerWidth||1;" +
        "return String(Math.min(1,Math.max(0,(x+vw)/w)));})()"
