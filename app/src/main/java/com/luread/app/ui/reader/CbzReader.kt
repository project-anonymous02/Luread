package com.luread.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.luread.app.core.design.LoadingState
import kotlinx.coroutines.launch
import java.io.File

/**
 * Pembaca CBZ (Comic Book ZIP): satu gambar per halaman dalam
 * [HorizontalPager] (LUREAD-FIX: geser kiri/kanan saja, tanpa gulir
 * vertikal; [reverseLayout] untuk arah kanan-ke-kiri).
 *
 * Gambar sudah diekstrak ke cache oleh [ReaderViewModel.openCbz].
 * `ContentScale.Fit` dipakai agar seluruh halaman komik terlihat tanpa
 * perlu menggulir ke bawah.
 */
@Composable
fun CbzReader(
    imagePaths: List<String>,
    startPage: Int,
    onPage: (pageIndex: Int, pageCount: Int) -> Unit,
    modifier: Modifier = Modifier,
    rtl: Boolean = false,
    animatePages: Boolean = true,
    showPageNumbers: Boolean = true,
    nav: ReaderNav? = null,
) {
    if (imagePaths.isEmpty()) {
        LoadingState(modifier = modifier)
        return
    }

    val pagerState = rememberPagerState(
        initialPage = startPage.coerceIn(0, imagePaths.lastIndex),
        pageCount = { imagePaths.size },
    )

    LaunchedEffect(pagerState.currentPage, imagePaths.size) {
        onPage(pagerState.currentPage, imagePaths.size)
    }

    val scope = rememberCoroutineScope()
    DisposableEffect(nav, pagerState) {
        nav?.register { delta ->
            val target = (pagerState.currentPage + delta).coerceIn(0, (pagerState.pageCount - 1).coerceAtLeast(0))
            if (target != pagerState.currentPage) {
                scope.launch {
                    if (animatePages) pagerState.animateScrollToPage(target)
                    else pagerState.scrollToPage(target)
                }
            }
        }
        onDispose { nav?.register(null) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 4.dp,
            reverseLayout = rtl,
            userScrollEnabled = true,
        ) { index ->
            if (kotlin.math.abs(pagerState.currentPage - index) <= 1) {
                AsyncImage(
                    model = File(imagePaths[index]),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                )
            }
        }

        if (showPageNumbers) {
            Text(
                text = "${pagerState.currentPage + 1} / ${imagePaths.size}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xCCFFFFFF),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .background(Color(0x66000000), MaterialTheme.shapes.small)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}
