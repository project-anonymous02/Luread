package com.luread.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luread.app.R
import com.luread.app.core.design.LureadSpace.SectionGap
import com.luread.app.core.design.LureadSpace.Sm
import com.luread.app.core.design.LureadSpace.Xs
import com.luread.app.core.design.SectionHeader
import com.luread.app.core.design.SkeletonRows
import com.luread.app.core.design.StateMessage
import com.luread.app.core.util.Format
import com.luread.app.ui.components.BookCard
import com.luread.app.ui.components.BookGridItem
import com.luread.app.ui.components.BookRowItem
import com.luread.app.ui.components.toCard
import com.luread.app.ui.manga.MangaHomeRow

@Composable
fun HomeScreen(
    onOpenBook: (String) -> Unit,
    onOpenDiscover: () -> Unit,
    onOpenMangaList: () -> Unit,
    onOpenManga: (String) -> Unit,
) {
    val vm = appViewModel { HomeViewModel(it.library, it.catalog, it.manga) }
    val continueReading by vm.continueReading.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val featured by vm.featured.collectAsStateWithLifecycle()
    val indonesian by vm.indonesian.collectAsStateWithLifecycle()
    val children by vm.children.collectAsStateWithLifecycle()
    val recommendations by vm.recommendations.collectAsStateWithLifecycle()
    val knowledge by vm.knowledge.collectAsStateWithLifecycle()
    val mangaState by vm.manga.collectAsStateWithLifecycle()
    val comics by vm.comics.collectAsStateWithLifecycle()
    val settingsVm = appViewModel { SettingsViewModel(it.settings, it.library) }
    val settings by settingsVm.state.collectAsStateWithLifecycle()
    val showManga = settings.showMangaHome

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(SectionGap),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Xs)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.home_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatsRow(
                    books = stats.libraryCount,
                    downloaded = stats.downloadedCount,
                    todayMillis = stats.todayMillis,
                )
            }
        }

        if (continueReading.isNotEmpty()) {
            item {
                SectionHeader(title = stringResource(R.string.home_continue))
                LazyRow(
                    contentPadding = PaddingValues(top = Sm),
                    horizontalArrangement = Arrangement.spacedBy(Sm),
                ) {
                    items(continueReading, key = { "cont-${it.id}" }) { card ->
                        ContinueCard(card = card, onClick = { onOpenBook(card.id) })
                    }
                }
            }
        }

        item {
            HomeBookRow(
                title = stringResource(R.string.home_featured),
                state = featured,
                keyPrefix = "feat",
                onOpenBook = onOpenBook,
                onSeeAll = onOpenDiscover,
                onRetry = vm::refresh,
            )
        }

        item {
            HomeBookRow(
                title = stringResource(R.string.home_indonesian),
                state = indonesian,
                keyPrefix = "indo",
                onOpenBook = onOpenBook,
                onSeeAll = onOpenDiscover,
                onRetry = vm::refresh,
            )
        }

        item {
            HomeBookRow(
                title = stringResource(R.string.home_children),
                state = children,
                keyPrefix = "child",
                onOpenBook = onOpenBook,
                onSeeAll = onOpenDiscover,
                onRetry = vm::refresh,
            )
        }

        item {
            HomeBookRow(
                title = stringResource(R.string.home_recommendations),
                state = recommendations,
                keyPrefix = "reco",
                onOpenBook = onOpenBook,
                onSeeAll = onOpenDiscover,
                onRetry = vm::refresh,
            )
        }

        item {
            HomeBookRow(
                title = stringResource(R.string.home_knowledge),
                state = knowledge,
                keyPrefix = "know",
                onOpenBook = onOpenBook,
                onSeeAll = onOpenDiscover,
                onRetry = vm::refresh,
            )
        }

        if (showManga) {
            item {
                MangaHomeRow(
                    state = mangaState,
                    onOpenManga = onOpenManga,
                    onSeeAll = onOpenMangaList,
                    onRetry = vm::refresh,
                )
            }
        }

        item {
            HomeBookRow(
                title = stringResource(R.string.home_comics),
                state = comics,
                keyPrefix = "comic",
                onOpenBook = onOpenBook,
                onSeeAll = onOpenDiscover,
                onRetry = vm::refresh,
            )
        }

        item {
            SectionHeader(title = stringResource(R.string.home_recent))
        }
        if (recent.isEmpty()) {
            item {
                StateMessage(
                    title = stringResource(R.string.state_empty_title),
                    subtitle = stringResource(R.string.home_empty_subtitle),
                )
            }
        } else {
            items(recent, key = { "recent-${it.id}" }) { card ->
                BookRowItem(card = card, onClick = { onOpenBook(card.id) })
            }
        }

        item {
            Text(
                text = stringResource(R.string.home_credit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Sm),
            )
        }
    }
}

/** Baris horizontal kartu buku yang dipakai beberapa section Home. */
@Composable
private fun HomeBookRow(
    title: String,
    state: CatalogUiState,
    keyPrefix: String,
    onOpenBook: (String) -> Unit,
    onSeeAll: () -> Unit,
    onRetry: () -> Unit,
) {
    SectionHeader(
        title = title,
        actionLabel = stringResource(R.string.action_see_all),
        onAction = onSeeAll,
    )
    when (state) {
        is CatalogUiState.Loading -> SkeletonRows(count = 2)
        is CatalogUiState.Error -> StateMessage(
            title = stringResource(R.string.state_error_title),
            subtitle = state.message,
            onRetry = onRetry,
        )
        is CatalogUiState.Content -> {
            val cards = state.page.results.map { it.toCard() }.take(12)
            if (cards.isEmpty()) {
                StateMessage(title = stringResource(R.string.state_empty_title))
            } else {
                LazyRow(
                    contentPadding = PaddingValues(top = Sm),
                    horizontalArrangement = Arrangement.spacedBy(Sm),
                ) {
                    items(cards, key = { "$keyPrefix-${it.id}" }) { card ->
                        BookGridItem(
                            card = card,
                            onClick = { onOpenBook(card.id) },
                            modifier = Modifier.width(132.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueCard(card: BookCard, onClick: () -> Unit) {
    BookGridItem(
        card = card,
        onClick = onClick,
        modifier = Modifier.width(132.dp),
    )
}

@Composable
private fun StatsRow(books: Int, downloaded: Int, todayMillis: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatChip(label = stringResource(R.string.home_stat_library, books))
        StatChip(label = stringResource(R.string.home_stat_offline, downloaded))
        StatChip(
            label = stringResource(
                R.string.home_stat_today,
                Format.duration(todayMillis / 60_000L),
            ),
        )
    }
}

@Composable
private fun StatChip(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(end = Xs)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
