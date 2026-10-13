package com.luread.app.ui.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.luread.app.R
import com.luread.app.core.design.LureadSpace.Lg
import com.luread.app.core.design.LureadSpace.Md
import com.luread.app.core.design.LureadSpace.Sm
import com.luread.app.core.design.SectionHeader
import com.luread.app.core.design.SkeletonRows
import com.luread.app.core.design.StateMessage
import com.luread.app.data.manga.MangaRepository
import com.luread.app.data.network.ApiResult
import com.luread.app.ui.appViewModel
import com.luread.app.ui.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MangaDetailUi {
    data object Loading : MangaDetailUi
    data class Content(
        val mangaId: String,
        val title: String,
        val coverUrl: String?,
        val authors: List<String>,
        val tags: List<String>,
        val status: String?,
        val description: String?,
        val chapters: List<MangaRepository.ChapterSummary>,
    ) : MangaDetailUi

    data class Error(val message: String) : MangaDetailUi
}

class MangaDetailViewModel(
    private val mangaId: String,
    private val manga: MangaRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow<MangaDetailUi>(MangaDetailUi.Loading)
    val ui: StateFlow<MangaDetailUi> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _ui.value = MangaDetailUi.Loading
        viewModelScope.launch {
            when (val r = manga.detail(mangaId)) {
                is ApiResult.Failure -> _ui.value = MangaDetailUi.Error(r.error.toUserMessage())
                is ApiResult.Success -> {
                    val (meta, chapters) = r.value
                    _ui.value = MangaDetailUi.Content(
                        mangaId = mangaId,
                        title = meta.title,
                        coverUrl = meta.coverUrl,
                        authors = meta.authorNames,
                        tags = meta.tags,
                        status = meta.status,
                        description = meta.description,
                        chapters = chapters,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaDetailScreen(
    mangaId: String,
    onBack: () -> Unit,
    onReadChapter: (String) -> Unit,
) {
    val vm = appViewModel { MangaDetailViewModel(mangaId, it.manga) }
    val ui by vm.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = (ui as? MangaDetailUi.Content)?.title ?: stringResource(R.string.manga_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        when (val state = ui) {
            is MangaDetailUi.Loading -> SkeletonRows(count = 6)
            is MangaDetailUi.Error -> StateMessage(
                title = stringResource(R.string.state_error_title),
                subtitle = state.message,
                onRetry = vm::refresh,
            )
            is MangaDetailUi.Content -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Lg),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(Sm),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Md)) {
                        val cover = state.coverUrl
                        if (cover != null) {
                            AsyncImage(
                                model = cover,
                                contentDescription = state.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(120.dp)
                                    .aspectRatio(0.7f),
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(XsSafe)) {
                            if (state.authors.isNotEmpty()) {
                                Text(
                                    text = state.authors.joinToString(", "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (state.status != null) {
                                Text(
                                    text = state.status,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (state.tags.isNotEmpty()) {
                                Text(
                                    text = state.tags.take(6).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                if (!state.description.isNullOrBlank()) {
                    item {
                        Text(
                            text = state.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    Text(
                        text = stringResource(R.string.manga_credit),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    SectionHeader(title = stringResource(R.string.manga_chapters, state.chapters.size))
                }
                if (state.chapters.isEmpty()) {
                    item {
                        StateMessage(title = stringResource(R.string.state_empty_title))
                    }
                } else {
                    items(state.chapters, key = { it.id }) { ch ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onReadChapter(ch.id) }
                                .padding(vertical = Sm),
                        ) {
                            Text(
                                text = buildString {
                                    append("Ch. ")
                                    append(ch.number ?: "?")
                                    if (!ch.title.isNullOrBlank()) {
                                        append(" — ")
                                        append(ch.title)
                                    }
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = listOfNotNull(
                                    ch.language,
                                    ch.pages?.let { "$it pp" },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

private val XsSafe = 4.dp
