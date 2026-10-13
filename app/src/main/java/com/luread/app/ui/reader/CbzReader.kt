package com.luread.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.luread.app.core.design.LoadingState
import java.io.File

/**
 * Pembaca CBZ (Comic Book ZIP) sederhana: menampilkan gambar per halaman
 * dalam HorizontalPager. Gambar sudah diekstrak ke cache oleh
 * [ReaderViewModel.openCbz].
 */
@Composable
fun CbzReader(
    imagePaths: List<String>,
    startPage: Int,
    onPage: (pageIndex: Int, pageCount: Int) -> Unit,
    modifier: Modifier = Modifier,
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

    HorizontalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        pageSpacing = 4.dp,
    ) { index ->
        if (kotlin.math.abs(pagerState.currentPage - index) <= 1) {
            AsyncImage(
                model = File(imagePaths[index]),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
