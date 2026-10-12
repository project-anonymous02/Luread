package com.luread.app.ui.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luread.app.R
import com.luread.app.core.design.StateMessage
import java.io.File

/**
 * Pembaca PDF berbasis `android.graphics.pdf.PdfRenderer` bawaan Android.
 *
 * Keputusan: tidak memakai adapter PDFium Readium agar tidak menambah
 * native library besar yang belum bisa diverifikasi di lingkungan build ini
 * (lihat docs/architecture.md). Renderer per halaman dibuat saat halaman
 * masuk viewport dan ditutup setelahnya.
 */
@Composable
fun PdfReader(
    path: String,
    startPage: Int,
    onPage: (pageIndex: Int, pageCount: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val file = remember(path) { File(path) }
    val renderer = remember(file) { openRenderer(file) }

    DisposableEffect(renderer) {
        onDispose { runCatching { renderer?.close() } }
    }

    if (renderer == null) {
        StateMessage(
            title = stringResource(R.string.pdf_error_title),
            subtitle = stringResource(R.string.pdf_error_subtitle),
        )
        return
    }

    val pageCount = renderer.pageCount
    if (pageCount <= 0) {
        StateMessage(
            title = stringResource(R.string.pdf_empty_title),
            subtitle = stringResource(R.string.pdf_empty_subtitle),
        )
        return
    }

    val pagerState = rememberPagerState(
        initialPage = startPage.coerceIn(0, pageCount - 1),
        pageCount = { pageCount },
    )

    LaunchedEffect(pagerState.currentPage, pageCount) {
        onPage(pagerState.currentPage, pageCount)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(ComposeColor(0xFF2B2B2B)),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 8.dp,
        ) { pageIndex ->
            val bitmap = remember(pageIndex, maxWidth, maxHeight, renderer) {
                renderPage(renderer, pageIndex, constraints.maxWidth, constraints.maxHeight)
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                )
            }
        }

        androidx.compose.material3.Text(
            text = stringResource(R.string.pdf_page_count, pagerState.currentPage + 1, pageCount),
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            color = ComposeColor.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp)
                .background(
                    ComposeColor(0x66000000),
                    androidx.compose.material3.MaterialTheme.shapes.small,
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private fun openRenderer(file: File): PdfRenderer? = runCatching {
    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    PdfRenderer(descriptor)
}.getOrNull()

private fun renderPage(renderer: PdfRenderer, index: Int, maxWidthPx: Int, maxHeightPx: Int): ImageBitmap? {
    if (maxWidthPx <= 0 || maxHeightPx <= 0) return null
    return runCatching {
        // Tidak memakai `use {}` agar aman pada API 26 tempat PdfRenderer.Page
        // belum mengimplementasikan AutoCloseable.
        val page = renderer.openPage(index)
        try {
            val width = page.width.toFloat()
            val height = page.height.toFloat()
            val scale = minOf(maxWidthPx / width, maxHeightPx / height).coerceAtLeast(0.05f)
            val bitmapWidth = (width * scale).toInt().coerceAtLeast(1)
            val bitmapHeight = (height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap.asImageBitmap()
        } finally {
            page.close()
        }
    }.getOrNull()
}
