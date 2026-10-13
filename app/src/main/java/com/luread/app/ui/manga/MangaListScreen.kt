package com.luread.app.ui.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.luread.app.R
import com.luread.app.core.design.LureadSpace.Md
import com.luread.app.core.design.LureadSpace.Sm
import com.luread.app.core.design.LureadSpace.Xs
import com.luread.app.core.design.SectionHeader
import com.luread.app.core.design.SkeletonRows
import com.luread.app.core.design.StateMessage
import com.luread.app.data.manga.MangaRepository
import com.luread.app.ui.appViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** Layar daftar manga (search + browse populer). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaListScreen(onOpenManga: (String) -> Unit, onBack: () -> Unit) {
    val vm = appViewModel { MangaListViewModel(it.manga) }
    val state by vm.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState, vm) {
        snapshotFlow {
            gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to gridState.layoutInfo.totalItemsCount
        }
            .filter { (visibleLast, total) -> visibleLast != null && total > 0 && visibleLast >= total - 4 }
            .distinctUntilChanged()
            .collect { vm.loadMore() }
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(stringResource(R.string.manga_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            var searchQuery by rememberSaveable { mutableStateOf("") }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    vm.onSearchChange(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                placeholder = { Text(stringResource(R.string.manga_search_hint)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            )

            when (val current = state) {
                is MangaListUi.Loading -> SkeletonRows(count = 6)
                is MangaListUi.Error -> StateMessage(
                    title = stringResource(R.string.state_error_title),
                    subtitle = current.message,
                    onRetry = vm::retry,
                )
                is MangaListUi.Content -> {
                    if (current.items.isEmpty()) {
                        StateMessage(title = stringResource(R.string.discover_empty_title))
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
                            items(current.items, key = { it.id }) { item ->
                                MangaGridItem(item = item, onClick = { onOpenManga(item.id) })
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
}

/** Baris manga di Home. */
@Composable
fun MangaHomeRow(
    state: MangaListUi,
    onOpenManga: (String) -> Unit,
    onSeeAll: () -> Unit,
    onRetry: () -> Unit,
) {
    SectionHeader(
        title = stringResource(R.string.home_manga),
        actionLabel = stringResource(R.string.action_see_all),
        onAction = onSeeAll,
    )
    when (state) {
        is MangaListUi.Loading -> SkeletonRows(count = 2)
        is MangaListUi.Error -> StateMessage(
            title = stringResource(R.string.state_error_title),
            subtitle = state.message,
            onRetry = onRetry,
        )
        is MangaListUi.Content -> {
            val items = state.items.take(12)
            if (items.isEmpty()) {
                StateMessage(title = stringResource(R.string.state_empty_title))
            } else {
                LazyRow(
                    contentPadding = PaddingValues(top = Sm),
                    horizontalArrangement = Arrangement.spacedBy(Sm),
                ) {
                    items(items, key = { "manga-${it.id}" }) { item ->
                        Column(
                            modifier = Modifier
                                .width(110.dp)
                                .clickable { onOpenManga(item.id) },
                        ) {
                            MangaCover(item = item, modifier = Modifier.fillMaxWidth())
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MangaGridItem(item: MangaRepository.MangaSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        MangaCover(item = item, modifier = Modifier.fillMaxWidth())
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (item.authorNames.isNotEmpty()) {
            Text(
                text = item.authorNames.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MangaCover(item: MangaRepository.MangaSummary, modifier: Modifier = Modifier) {
    val url = item.coverUrl
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(0.7f),
        )
    } else {
        // Fallback: inisial judul.
        androidx.compose.foundation.layout.Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = item.title.take(1).uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
