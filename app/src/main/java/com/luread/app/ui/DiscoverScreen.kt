package com.luread.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luread.app.R
import com.luread.app.core.design.LureadSpace.Md
import com.luread.app.core.design.LureadSpace.Xs
import com.luread.app.core.design.SkeletonRows
import com.luread.app.core.design.StateMessage
import com.luread.app.data.catalog.BookSource
import com.luread.app.ui.components.BookGridItem
import com.luread.app.ui.components.toCard
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
fun DiscoverScreen(onOpenBook: (String) -> Unit) {
    val vm = appViewModel { DiscoverViewModel(it.library, it.catalog) }
    val filters by vm.filters.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()

    val gridState = rememberLazyGridState()

    // Muat halaman berikutnya ketika baris terakhir mendekati viewport.
    LaunchedEffect(gridState, vm) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to gridState.layoutInfo.totalItemsCount }
            .filter { (visibleLast, total) -> visibleLast != null && total > 0 && visibleLast >= total - 4 }
            .distinctUntilChanged()
            .collect { vm.loadMore() }
    }

    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        OutlinedTextField(
            value = filters.search,
            onValueChange = vm::onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            placeholder = { Text(stringResource(R.string.discover_search_hint)) },
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                )
            },
        )

        FilterRow(
            label = stringResource(R.string.filter_source),
            options = sourceOptions,
            selected = filters.source.id,
            onSelect = { id ->
                val picked = BookSource.entries.firstOrNull { it.id == id }
                if (picked != null) vm.setSource(picked)
            },
        )
        FilterRow(
            label = stringResource(R.string.filter_language),
            options = languageOptions,
            selected = filters.language,
            onSelect = vm::setLanguage,
        )
        if (filters.source == BookSource.GUTENDEX) {
            FilterRow(
                label = stringResource(R.string.filter_topic),
                options = topicOptions,
                selected = filters.topic,
                onSelect = vm::setTopic,
            )
            FilterRow(
                label = stringResource(R.string.filter_format),
                options = formatOptions,
                selected = filters.mimeType,
                onSelect = vm::setMimeType,
            )
        }

        when (val current = state) {
            is CatalogUiState.Loading -> SkeletonRows(count = 6)

            is CatalogUiState.Error -> StateMessage(
                title = stringResource(R.string.state_error_title),
                subtitle = current.message,
                onRetry = vm::retry,
            )

            is CatalogUiState.Content -> {
                if (current.page.results.isEmpty()) {
                    StateMessage(
                        title = stringResource(R.string.discover_empty_title),
                        subtitle = stringResource(R.string.discover_empty_subtitle),
                    )
                } else {
                    val columns = com.luread.app.core.design.LocalLureadExtras.current.gridColumns
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = Md, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(Md),
                        verticalArrangement = Arrangement.spacedBy(Md),
                    ) {
                        items(current.page.results, key = { it.key }) { book ->
                            BookGridItem(
                                card = book.toCard(),
                                onClick = { onOpenBook(book.key) },
                            )
                        }
                        if (current.isLoadingMore) {
                            item(span = { GridItemSpan(columns) }) {
                                Text(
                                    text = stringResource(R.string.state_loading),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Xs),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class ChipOption(val value: String?, @StringRes val labelRes: Int)

private val sourceOptions = listOf(
    ChipOption(BookSource.GUTENDEX.id, R.string.filter_source_gutenberg),
    ChipOption(BookSource.OPEN_LIBRARY.id, R.string.filter_source_openlibrary),
    ChipOption(BookSource.INTERNET_ARCHIVE.id, R.string.filter_source_archive),
)

private val languageOptions = listOf(
    ChipOption(null, R.string.filter_any_language),
    ChipOption("en", R.string.filter_lang_en),
    ChipOption("fr", R.string.filter_lang_fr),
    ChipOption("de", R.string.filter_lang_de),
    ChipOption("es", R.string.filter_lang_es),
    ChipOption("it", R.string.filter_lang_it),
    ChipOption("nl", R.string.filter_lang_nl),
    ChipOption("pt", R.string.filter_lang_pt),
    ChipOption("ru", R.string.filter_lang_ru),
    ChipOption("zh", R.string.filter_lang_zh),
    ChipOption("fi", R.string.filter_lang_fi),
)

private val topicOptions = listOf(
    ChipOption(null, R.string.filter_any_topic),
    ChipOption("children", R.string.filter_topic_children),
    ChipOption("poetry", R.string.filter_topic_poetry),
)

private val formatOptions = listOf(
    ChipOption(null, R.string.filter_any_format),
    ChipOption("application/epub+zip", R.string.filter_format_epub),
    ChipOption("text/html", R.string.filter_format_html),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(
    label: String,
    options: List<ChipOption>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = Xs),
        horizontalArrangement = Arrangement.spacedBy(Xs),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        options.forEach { option ->
            FilterChip(
                selected = selected == option.value,
                onClick = { onSelect(option.value) },
                label = {
                    Text(
                        text = stringResource(option.labelRes),
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
            )
        }
    }
}
