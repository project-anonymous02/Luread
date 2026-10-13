package com.luread.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luread.app.R
import com.luread.app.core.design.LureadSpace.Lg
import com.luread.app.core.design.LureadSpace.Sm
import com.luread.app.core.design.LureadSpace.Xs
import com.luread.app.core.design.SectionHeader
import com.luread.app.core.design.StateMessage
import com.luread.app.ui.components.BookRowItem
import com.luread.app.ui.components.toCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onOpenBook: (String) -> Unit) {
    val vm = appViewModel { LibraryViewModel(it.library, it.appContext) }
    val books by vm.books.collectAsStateWithLifecycle()
    val filter by vm.filterState.collectAsStateWithLifecycle()
    val sort by vm.sortState.collectAsStateWithLifecycle()
    val search by vm.searchState.collectAsStateWithLifecycle()
    val collections by vm.collectionNames.collectAsStateWithLifecycle()
    val collectionFilter by vm.collectionFilterState.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var collectionTarget by remember { mutableStateOf<String?>(null) }
    var collectionDraft by remember { mutableStateOf("") }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) vm.import(uri) }

    Column(modifier = Modifier.fillMaxSize()) {
        message?.let { text ->
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Lg, vertical = Xs)
                    .clickable(onClick = vm::dismissMessage),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(Sm),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Lg, vertical = Sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.nav_library),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            TextButton(
                onClick = {
                    importLauncher.launch(
                        arrayOf(
                            "application/epub+zip",
                            "application/pdf",
                            "text/plain",
                            "text/html",
                            "application/zip",
                            "application/vnd.comicbook+zip",
                        ),
                    )
                },
            ) {
                Icon(
                    imageVector = Icons.Outlined.FileOpen,
                    contentDescription = null,
                    modifier = Modifier.padding(end = Xs),
                )
                Text(stringResource(R.string.action_import))
            }
        }

        OutlinedTextField(
            value = search,
            onValueChange = vm::onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Lg, vertical = Xs),
            placeholder = { Text(stringResource(R.string.library_search_hint)) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Lg),
            horizontalArrangement = Arrangement.spacedBy(Xs),
        ) {
            LibraryViewModel.Filter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = {
                        vm.setFilter(option)
                        if (option == LibraryViewModel.Filter.COLLECTION && collectionFilter == null) {
                            collections.firstOrNull()?.let(vm::setCollectionFilter)
                        }
                    },
                    label = {
                        Text(
                            text = stringResource(option.labelRes),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                )
            }
        }

        if (filter == LibraryViewModel.Filter.COLLECTION) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Lg),
                horizontalArrangement = Arrangement.spacedBy(Xs),
            ) {
                collections.forEach { name ->
                    FilterChip(
                        selected = collectionFilter == name,
                        onClick = { vm.setCollectionFilter(name) },
                        label = { Text(name, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Lg),
            horizontalArrangement = Arrangement.spacedBy(Xs),
        ) {
            LibraryViewModel.Sort.entries.forEach { option ->
                FilterChip(
                    selected = sort == option,
                    onClick = { vm.setSort(option) },
                    label = {
                        Text(
                            text = stringResource(option.labelRes),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                )
            }
        }

        if (books.isEmpty()) {
            StateMessage(
                title = stringResource(R.string.library_empty_title),
                subtitle = stringResource(R.string.library_empty_subtitle),
            )
        } else {
            SectionHeader(
                title = stringResource(R.string.library_count, books.size),
                modifier = Modifier.padding(horizontal = Lg, vertical = Sm),
            )
            LazyColumn(
                contentPadding = PaddingValues(start = Lg, end = Lg, bottom = 24.dp),
            ) {
                items(books, key = { it.id }) { book ->
                    BookRowItem(
                        card = book.toCard(),
                        onClick = { onOpenBook(book.id) },
                        trailing = {
                            Row {
                                IconButton(onClick = {
                                    collectionTarget = book.id
                                    collectionDraft = book.collectionName.orEmpty()
                                }) {
                                    Icon(
                                        imageVector = Icons.Outlined.BookmarkAdd,
                                        contentDescription = stringResource(R.string.library_assign_collection),
                                        tint = if (book.collectionName != null) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                                IconButton(onClick = { vm.toggleFavorite(book.id, !book.favorite) }) {
                                    Icon(
                                        imageVector = if (book.favorite) {
                                            Icons.Outlined.Favorite
                                        } else {
                                            Icons.Outlined.FavoriteBorder
                                        },
                                        contentDescription = null,
                                        tint = if (book.favorite) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    collectionTarget?.let { bookId ->
        AlertDialog(
            onDismissRequest = { collectionTarget = null },
            title = { Text(stringResource(R.string.library_assign_collection)) },
            text = {
                OutlinedTextField(
                    value = collectionDraft,
                    onValueChange = { collectionDraft = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.library_collection_hint)) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.assignCollection(bookId, collectionDraft)
                        collectionTarget = null
                    },
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { collectionTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
