package com.luread.app.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.luread.app.R
import com.luread.app.core.design.StateMessage
import org.readium.navigator.common.Selection
import org.readium.navigator.web.reflowable.ReflowableWebRendition
import org.readium.navigator.web.reflowable.ReflowableWebRenditionState
import org.readium.r2.shared.ExperimentalReadiumApi

/**
 * Konten EPUB.
 *
 * Alur mengikuti dokumentasi resmi Readium 3.4 (docs/ui-research.md):
 * state dirender oleh [ReflowableWebRendition], lokasi dipantau lewat
 * `controller.location` (sumber Compose State), dan teks terpilih diambil
 * lewat `controller.currentSelection()` untuk diteruskan ke penerjemah.
 */
@OptIn(ExperimentalReadiumApi::class)
@Composable
fun EpubContent(
    state: ReflowableWebRenditionState,
    viewModel: ReaderViewModel,
    modifier: Modifier = Modifier,
) {
    val controller = state.controller

    LaunchedEffect(controller) {
        if (controller == null) return@LaunchedEffect
        snapshotFlow { controller.location }
            .collect { location ->
                viewModel.onEpubLocation(
                    progress = location.totalProgression.value,
                    href = location.href.toString(),
                    text = location.position.value.toString(),
                )
            }
    }

    Box(modifier = modifier.fillMaxSize()) {
        ReflowableWebRendition(state = state)
    }
}

@OptIn(ExperimentalReadiumApi::class)
@Composable
fun EpubLoading(message: String? = null, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxSize(fraction = 0.8f),
            )
        } else {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun EpubError(message: String, onRetry: (() -> Unit)? = null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        StateMessage(
            title = stringResource(R.string.epub_error_title),
            subtitle = message,
            onRetry = onRetry,
        )
    }
}

/** Ambil teks yang sedang diseleksi pengguna, bila ada. */
@OptIn(ExperimentalReadiumApi::class)
suspend fun selectedEpubText(state: ReflowableWebRenditionState): Selection<*>? {
    val controller = state.controller ?: return null
    return controller.currentSelection()
}
