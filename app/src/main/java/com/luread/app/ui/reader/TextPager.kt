package com.luread.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luread.app.core.design.LoadingState

/**
 * Pembaca file teks.
 *
 * Teks dipaginasi pada tingkat karakter (lihat [com.luread.app.core.util.TxtDocument])
 * sehingga halaman tidak pernah memuat seluruh file sekaligus.
 */
@Composable
fun TextPager(
    pages: List<String>,
    startPage: Int,
    fontSp: Float,
    onPage: (pageIndex: Int, pageCount: Int) -> Unit,
    modifier: Modifier = Modifier,
    lineHeightMul: Float = 1.4f,
    animatePages: Boolean = true,
) {
    if (pages.isEmpty()) {
        LoadingState(modifier = modifier)
        return
    }

    val pagerState = rememberPagerState(
        initialPage = startPage.coerceIn(0, pages.lastIndex),
        pageCount = { pages.size },
    )

    LaunchedEffect(pagerState.currentPage, pages.size) {
        onPage(pagerState.currentPage, pages.size)
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxSize()
            .background(Color.White),
        pageSpacing = 8.dp,
        userScrollEnabled = animatePages,
    ) { index ->
        // Halaman yang sedang tidak terlihat tidak perlu digambar.
        if (kotlin.math.abs(pagerState.currentPage - index) <= 1) {
            Text(
                text = pages[index],
                fontSize = fontSp.sp,
                lineHeight = (fontSp * lineHeightMul.coerceIn(1.0f, 2.2f)).sp,
                fontFamily = FontFamily.Serif,
                color = Color.Black,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
    }
}
