package com.luread.app.ui.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ChevronLeft
import androidx.compose.material.icons.automirrored.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.luread.app.R
import com.luread.app.core.design.LoadingState
import com.luread.app.data.manga.MangaRepository
import com.luread.app.data.network.ApiResult
import com.luread.app.ui.appViewModel
import com.luread.app.ui.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MangaReaderUi {
    data object Loading : MangaReaderUi
    data class Content(
        val pages: List<String>,
        val prevChapterId: String?,
        val nextChapterId: String?,
        val chapterLabel: String,
    ) : MangaReaderUi

    data class Error(val message: String) : MangaReaderUi
}

class MangaReaderViewModel(
    private val chapterId: String,
    private val mangaId: String?,
    private val manga: MangaRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow<MangaReaderUi>(MangaReaderUi.Loading)
    val ui: StateFlow<MangaReaderUi> = _ui.asStateFlow()

    private var allChapters: List<MangaRepository.ChapterSummary> = emptyList()

    init {
        load()
    }

    private fun load() {
        _ui.value = MangaReaderUi.Loading
        viewModelScope.launch {
            // Prefetch daftar chapter untuk navigasi prev/next.
            if (mangaId != null && allChapters.isEmpty()) {
                when (val c = manga.chapters(mangaId)) {
                    is ApiResult.Success -> allChapters = c.value
                    is ApiResult.Failure -> { /* navigasi opsional */ }
                }
            }
            when (val r = manga.pageUrls(chapterId)) {
                is ApiResult.Failure -> _ui.value = MangaReaderUi.Error(r.error.toUserMessage())
                is ApiResult.Success -> {
                    if (r.value.isEmpty()) {
                        _ui.value = MangaReaderUi.Error("No pages in this chapter")
                    } else {
                        resolveNavigation(r.value)
                    }
                }
            }
        }
    }

    private fun resolveNavigation(pages: List<String>) {
        val current = allChapters.indexOfFirst { it.id == chapterId }
        val prev = if (current > 0) allChapters[current - 1].id else null
        val next = if (current >= 0 && current < allChapters.lastIndex) allChapters[current + 1].id else null
        val label = if (current >= 0) {
            val ch = allChapters[current]
            "Ch. ${ch.number ?: "?"}"
        } else {
            ""
        }
        _ui.value = MangaReaderUi.Content(pages, prev, next, label)
    }
}

/** Pembaca chapter manga: pager horizontal gambar (vertical scroll per halaman). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaReaderScreen(
    chapterId: String,
    mangaId: String? = null,
    onBack: () -> Unit,
    onNavigateChapter: ((String) -> Unit)? = null,
) {
    val vm = appViewModel { MangaReaderViewModel(chapterId, mangaId, it.manga) }
    val ui by vm.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val label = (ui as? MangaReaderUi.Content)?.chapterLabel
                    Text(
                        text = label?.takeIf { it.isNotEmpty() }
                            ?: stringResource(R.string.manga_reading),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
        bottomBar = {
            val content = ui as? MangaReaderUi.Content ?: return@Scaffold
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { content.prevChapterId?.let { onNavigateChapter?.invoke(it) } },
                    enabled = content.prevChapterId != null,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ChevronLeft, contentDescription = "Previous chapter")
                }
                Text(
                    text = "${content.chapterLabel} · ${content.pages.size} pp",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                    onClick = { content.nextChapterId?.let { onNavigateChapter?.invoke(it) } },
                    enabled = content.nextChapterId != null,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ChevronRight, contentDescription = "Next chapter")
                }
            }
        },
    ) { padding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.White),
        ) {
            when (val state = ui) {
                is MangaReaderUi.Loading -> LoadingState()
                is MangaReaderUi.Error -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                is MangaReaderUi.Content -> {
                    val pagerState = rememberPagerState(
                        initialPage = 0,
                        pageCount = { state.pages.size },
                    )
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        pageSpacing = 2.dp,
                    ) { index ->
                        if (kotlin.math.abs(pagerState.currentPage - index) <= 1) {
                            AsyncImage(
                                model = state.pages[index],
                                contentDescription = null,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}
