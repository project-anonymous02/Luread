package com.luread.app.ui.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    data class Content(val pages: List<String>) : MangaReaderUi
    data class Error(val message: String) : MangaReaderUi
}

class MangaReaderViewModel(
    private val chapterId: String,
    private val manga: MangaRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow<MangaReaderUi>(MangaReaderUi.Loading)
    val ui: StateFlow<MangaReaderUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            when (val r = manga.pageUrls(chapterId)) {
                is ApiResult.Failure -> _ui.value = MangaReaderUi.Error(r.error.toUserMessage())
                is ApiResult.Success -> {
                    if (r.value.isEmpty()) {
                        _ui.value = MangaReaderUi.Error("No pages in this chapter")
                    } else {
                        _ui.value = MangaReaderUi.Content(r.value)
                    }
                }
            }
        }
    }
}

/** Pembaca chapter manga: pager horizontal gambar (vertical scroll per halaman). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaReaderScreen(
    chapterId: String,
    onBack: () -> Unit,
) {
    val vm = appViewModel { MangaReaderViewModel(chapterId, it.manga) }
    val ui by vm.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manga_reading)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
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
