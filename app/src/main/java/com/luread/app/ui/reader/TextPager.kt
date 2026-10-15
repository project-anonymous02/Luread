package com.luread.app.ui.reader

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luread.app.core.design.LureadSpace.Xs
import com.luread.app.core.design.LocalLureadExtras
import com.luread.app.core.design.LoadingState
import com.luread.app.core.util.PagePaginator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Ukuran padding vertikal total halaman teks (dipakai saat menghitung baris per halaman). */
private const val PAGE_VERTICAL_PADDING_DP = 16

/** Batas ukuran potongan teks yang diukur sekaligus agar layout tetap cepat. */
private const val MEASURE_CHUNK_CHARS = 20_000

/** Cadangan jumlah karakter per halaman bila pengukuran gagal. */
private const val FALLBACK_CHARS_PER_PAGE = 2_000

/**
 * Pembaca file teks.
 *
 * LUREAD-FIX: halaman TIDAK BISA digulir vertikal. Teks dipaginasi pada
 * tingkat baris sesuai ukuran layar lewat [PagePaginator] + pengukuran
 * layout asli, lalu ditampilkan dengan [HorizontalPager] (geser
 * kiri/kanan, [reverseLayout] untuk arah kanan-ke-kiri).
 */
@Composable
fun TextPager(
    text: String,
    startPage: Int,
    fontSp: Float,
    onPage: (pageIndex: Int, pageCount: Int) -> Unit,
    modifier: Modifier = Modifier,
    lineHeightMul: Float = 1.4f,
    animatePages: Boolean = true,
    animationStyle: String = "slide",
    rtl: Boolean = false,
    fontFamily: FontFamily = FontFamily.Serif,
    justify: Boolean = false,
    showPageNumbers: Boolean = true,
    nav: ReaderNav? = null,
    onPagesReady: (List<String>) -> Unit = {},
) {
    val density = LocalDensity.current
    val extras = LocalLureadExtras.current
    val style = remember(fontSp, lineHeightMul, fontFamily, justify) {
        TextStyle(
            fontSize = fontSp.sp,
            lineHeight = (fontSp * lineHeightMul.coerceIn(1.0f, 2.2f)).sp,
            fontFamily = fontFamily,
            textAlign = if (justify) TextAlign.Justify else TextAlign.Start,
            color = Color.Black,
        )
    }
    val fallbackLinePx = with(density) { (fontSp * lineHeightMul.coerceIn(1.0f, 2.2f)).sp.toPx() }

    var size by remember { mutableStateOf(IntSize.Zero) }
    var anchor by remember { mutableStateOf(startPage) }
    var pages by remember(text, style) { mutableStateOf<List<String>?>(null) }

    val measurer = rememberTextMeasurerCompat()
    val measureMutex = remember { Mutex() }

    LaunchedEffect(text, style, size) {
        if (size.width <= 0 || size.height <= 0) return@LaunchedEffect
        val paddingPx = with(density) { (PAGE_VERTICAL_PADDING_DP * 2).dp.toPx() }
        val measured = withContext(Dispatchers.Default) {
            measureMutex.withLock {
                measureVisualPages(
                    measurer = measurer,
                    text = text,
                    style = style,
                    widthPx = size.width,
                    heightPx = size.height,
                    verticalPaddingPx = paddingPx,
                    fallbackLineHeightPx = fallbackLinePx,
                )
            }
        }
        pages = measured
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .onSizeChanged { size = it },
    ) {
        val current = pages
        if (current == null) {
            LoadingState(modifier = Modifier.align(Alignment.Center))
            return@Box
        }
        if (current.isEmpty()) {
            LoadingState(modifier = Modifier.align(Alignment.Center))
            return@Box
        }

        LaunchedEffect(current) { onPagesReady(current) }

        // pageCount membaca state langsung sehingga tetap benar saat hasil
        // pengukuran ulang (ganti ukuran huruf) datang.
        val pagerState = androidx.compose.foundation.pager.rememberPagerState(
            initialPage = anchor.coerceIn(0, current.lastIndex),
            pageCount = { pages?.size ?: 0 },
        )
        var restored by remember { mutableStateOf(false) }

        LaunchedEffect(current, restored) {
            val list = current
            if (!restored && list.isNotEmpty()) {
                restored = true
                val target = anchor.coerceIn(0, list.lastIndex)
                if (target != pagerState.currentPage) pagerState.scrollToPage(target)
            }
            if (list.isNotEmpty() && pagerState.currentPage > list.lastIndex) {
                pagerState.scrollToPage(list.lastIndex)
            }
        }

        LaunchedEffect(pagerState.currentPage, current.size) {
            anchor = pagerState.currentPage
            onPage(pagerState.currentPage, current.size)
        }

        val scope = rememberCoroutineScope()
        DisposableEffect(nav, pagerState) {
            nav?.register { delta ->
                val last = (pagerState.pageCount - 1).coerceAtLeast(0)
                val target = (pagerState.currentPage + delta).coerceIn(0, last)
                if (target != pagerState.currentPage) {
                    scope.launch {
                        if (animatePages && !extras.reduceMotion) {
                            pagerState.animateScrollToPage(target)
                        } else {
                            pagerState.scrollToPage(target)
                        }
                    }
                }
            }
            onDispose { nav?.register(null) }
        }

        val snapSpec: AnimationSpec<Float> = if (animatePages && !extras.reduceMotion) {
            spring(stiffness = Spring.StiffnessMediumLow)
        } else {
            // LUREAD-FIX: animasi dimatikan -> snap langsung tanpa gerak.
            tween(durationMillis = 1)
        }
        val fling = PagerDefaults.flingBehavior(
            state = pagerState,
            snapAnimationSpec = snapSpec,
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 8.dp,
            // LUREAD-FIX: geser horizontal saja; arah rtl membalik letak halaman.
            reverseLayout = rtl,
            userScrollEnabled = true,
            flingBehavior = fling,
        ) { index ->
            // LUREAD-FIX: tanpa verticalScroll — teks sudah pas satu layar.
            if (kotlin.math.abs(pagerState.currentPage - index) <= 1) {
                val offset =
                    (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction
                val fade = animationStyle == "fade" && animatePages && !extras.reduceMotion
                val pageAlpha = if (fade) {
                    1f - (kotlin.math.abs(offset).coerceIn(0f, 1f) * 0.7f)
                } else {
                    1f
                }
                Text(
                    text = current[index],
                    style = style,
                    color = Color.Black,
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(pageAlpha)
                        .padding(horizontal = 20.dp, vertical = PAGE_VERTICAL_PADDING_DP.dp),
                )
            }
        }

        if (showPageNumbers) {
            Text(
                text = "${pagerState.currentPage + 1} / ${current.size}",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = Color(0xCC000000),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Xs * 2)
                    .background(
                        Color(0x66000000),
                        androidx.compose.material3.MaterialTheme.shapes.small,
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * Ukur teks menjadi halaman yang pas muat di layar.
 *
 * Teks besar dipecah dulu per [MEASURE_CHUNK_CHARS] (batas kata) agar satu
 * pemanggilan layout tidak menyangkut seluruh file; tiap potongan diukur
 * lalu hasilnya digabung. Semua kegagalan jatuh ke [PagePaginator.paginateByChars].
 */
internal fun measureVisualPages(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    widthPx: Int,
    heightPx: Int,
    verticalPaddingPx: Float,
    fallbackLineHeightPx: Float,
): List<String> {
    if (text.isEmpty()) return listOf("")
    if (widthPx <= 0 || heightPx <= 0) {
        return PagePaginator.paginateByChars(text, FALLBACK_CHARS_PER_PAGE)
    }
    if (text.length <= MEASURE_CHUNK_CHARS) {
        return measureChunkPages(measurer, text, style, widthPx, heightPx, verticalPaddingPx, fallbackLineHeightPx)
    }

    val pages = ArrayList<String>(text.length / FALLBACK_CHARS_PER_PAGE + 1)
    for (chunk in PagePaginator.paginateByChars(text, MEASURE_CHUNK_CHARS)) {
        pages += measureChunkPages(measurer, chunk, style, widthPx, heightPx, verticalPaddingPx, fallbackLineHeightPx)
    }
    return pages
}

private fun measureChunkPages(
    measurer: TextMeasurer,
    chunk: String,
    style: TextStyle,
    widthPx: Int,
    heightPx: Int,
    verticalPaddingPx: Float,
    fallbackLineHeightPx: Float,
): List<String> = runCatching {
    val layout = measurer.measure(
        text = chunk,
        style = style,
        constraints = Constraints(maxWidth = widthPx),
    )
    val lineCount = layout.lineCount
    if (lineCount <= 0) return PagePaginator.paginateByChars(chunk, FALLBACK_CHARS_PER_PAGE)
    val measuredLineHeight = if (lineCount > 1) {
        (layout.getLineBottom(lineCount - 1) - layout.getLineTop(0)) / lineCount
    } else {
        layout.getLineBottom(0) - layout.getLineTop(0)
    }
    val lineHeight = measuredLineHeight.takeIf { it > 0f && it.isFinite() } ?: fallbackLineHeightPx
    val cap = PagePaginator.linesPerPage(heightPx - verticalPaddingPx, lineHeight)
    PagePaginator.paginate(chunk, lineCount, cap) { line -> layout.getLineEnd(line) }
}.getOrElse {
    PagePaginator.paginateByChars(chunk, FALLBACK_CHARS_PER_PAGE)
}

/** [androidx.compose.ui.text.rememberTextMeasurer] dibungkus agar bisa di-stub di bawah. */
@Composable
private fun rememberTextMeasurerCompat(): TextMeasurer =
    androidx.compose.ui.text.rememberTextMeasurer()

/** Mapping nama keluarga huruf dari Settings ke [FontFamily] pembaca. */
fun readerFontFamily(name: String): FontFamily = when (name) {
    "sans" -> FontFamily.SansSerif
    "monospace" -> FontFamily.Monospace
    "cursive" -> FontFamily.Cursive
    else -> FontFamily.Serif
}
