package com.luread.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luread.app.R
import com.luread.app.core.design.BookCover
import com.luread.app.core.design.LureadSpace.Lg
import com.luread.app.core.design.LureadSpace.Md
import com.luread.app.core.design.LureadSpace.SectionGap
import com.luread.app.core.design.LureadSpace.Sm
import com.luread.app.core.design.LureadSpace.Xs
import com.luread.app.core.design.LureadTopBar
import com.luread.app.core.design.LoadingState
import com.luread.app.core.design.MetaRow
import com.luread.app.core.design.PrimaryActionButton
import com.luread.app.core.design.ProgressLine
import com.luread.app.core.design.SectionHeader
import com.luread.app.core.design.StateMessage
import com.luread.app.core.util.Format
import com.luread.app.data.db.BookEntity
import com.luread.app.data.downloads.DownloadManager

@Composable
fun BookDetailScreen(
    bookId: String,
    onBack: () -> Unit,
    onRead: (String) -> Unit,
    onTranslate: (String) -> Unit,
) {
    val vm = appViewModel { BookDetailViewModel(bookId, it.library, it.catalog, it.downloads) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val loading by vm.isLoading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val downloadError by vm.downloadError.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        LureadTopBar(
            title = ui?.title ?: "",
            onBack = onBack,
            actions = {
                val current = ui
                if (current != null) {
                    androidx.compose.material3.IconButton(onClick = vm::toggleFavorite) {
                        androidx.compose.material3.Icon(
                            imageVector = if (current.favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
        )

        val current = ui
        when {
            current == null && loading -> LoadingState()
            current == null -> StateMessage(
                title = stringResource(R.string.state_error_title),
                subtitle = error ?: stringResource(R.string.detail_book_unavailable),
                onRetry = vm::refresh,
            )
            else -> DetailContent(
                state = current,
                downloadError = downloadError,
                onRead = { onRead(bookId) },
                onDownload = vm::download,
                onCancelDownload = vm::cancelDownload,
                onAddToLibrary = vm::addToLibrary,
                onRemove = vm::removeFromLibrary,
                onDeleteFile = vm::deleteLocalFile,
                onTranslate = onTranslate,
            )
        }
    }
}

@Composable
private fun DetailContent(
    state: BookDetailUi,
    downloadError: String?,
    onRead: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onAddToLibrary: () -> Unit,
    onRemove: () -> Unit,
    onDeleteFile: () -> Unit,
    onTranslate: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Lg, end = Lg, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(Sm),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Lg)) {
                BookCover(
                    title = state.title,
                    coverUrl = state.coverUrl,
                    modifier = Modifier.width(120.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(Xs)) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (state.authorsText.isNotBlank()) {
                        Text(
                            text = state.authorsText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.progress > 0f) {
                        ProgressLine(progress = state.progress)
                        Text(
                            text = Format.percent(state.progress) +
                                if (state.finished) " · " + stringResource(R.string.detail_finished) else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        item {
            Actions(
                state = state,
                downloadError = downloadError,
                onRead = onRead,
                onDownload = onDownload,
                onCancelDownload = onCancelDownload,
                onAddToLibrary = onAddToLibrary,
                onRemove = onRemove,
                onDeleteFile = onDeleteFile,
                modifier = Modifier.padding(top = Xs),
            )
        }

        state.summary?.let { summary ->
            item {
                SectionHeader(title = stringResource(R.string.detail_about))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                androidx.compose.material3.TextButton(onClick = { onTranslate(summary) }) {
                    Text(
                        text = stringResource(R.string.action_translate),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        item {
            SectionHeader(title = stringResource(R.string.detail_info))
            Column(verticalArrangement = Arrangement.spacedBy(Xs)) {
                if (state.subjectsText.isNotBlank()) {
                    MetaRow(label = stringResource(R.string.detail_subjects), value = state.subjectsText)
                }
                if (state.languagesText.isNotBlank()) {
                    MetaRow(label = stringResource(R.string.detail_language), value = state.languagesText)
                }
                state.downloadCount?.let {
                    MetaRow(label = stringResource(R.string.detail_downloads), value = it.toString())
                }
                state.copyrightStatus?.let { status ->
                    MetaRow(
                        label = stringResource(R.string.detail_copyright),
                        value = stringResource(
                            if (status) R.string.detail_copyright_protected else R.string.detail_copyright_public,
                        ),
                    )
                }
                state.contentType?.let {
                    MetaRow(label = stringResource(R.string.detail_format), value = it)
                }
            }
        }

        if (state.formats.isNotEmpty()) {
            item {
                SectionHeader(title = stringResource(R.string.detail_formats))
            }
            items(state.formats) { format ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = format.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(
                            if (format.readable) R.string.detail_format_readable else R.string.detail_format_metadata_only,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.detail_source_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = SectionGap),
            )
        }
    }
}

@Composable
private fun Actions(
    state: BookDetailUi,
    downloadError: String?,
    onRead: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onAddToLibrary: () -> Unit,
    onRemove: () -> Unit,
    onDeleteFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Sm)) {
        when {
            state.downloadState == BookEntity.DownloadState.RUNNING -> {
                val done = state.downloadProgress?.doneBytes ?: 0L
                val total = state.downloadProgress?.totalBytes ?: -1L
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Sm),
                ) {
                    CircularProgressIndicator(modifier = Modifier.width(24.dp))
                    Text(
                        text = if (total > 0) "${Format.bytes(done)} / ${Format.bytes(total)}"
                        else Format.bytes(done),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    androidx.compose.material3.TextButton(onClick = onCancelDownload) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }

            state.canRead -> {
                PrimaryActionButton(
                    label = stringResource(R.string.action_read),
                    onClick = onRead,
                )
            }

            state.downloadState == BookEntity.DownloadState.FAILED -> {
                // LUREAD-FIX: tombol Retry muncul dulu, lalu pesan galat
                // (mis. "HTTP 503") supaya kegagalan tidak terlihat senyap.
                PrimaryActionButton(
                    label = stringResource(R.string.action_retry),
                    onClick = onDownload,
                )
                downloadError?.let {
                    Text(
                        text = stringResource(R.string.detail_download_failed, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            state.canDownload -> {
                PrimaryActionButton(
                    label = stringResource(R.string.action_download),
                    onClick = onDownload,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Sm)) {
            if (!state.inLibrary) {
                androidx.compose.material3.OutlinedButton(onClick = onAddToLibrary) {
                    Text(stringResource(R.string.action_add_library))
                }
            } else {
                if (state.localPath != null) {
                    androidx.compose.material3.OutlinedButton(onClick = onDeleteFile) {
                        Text(stringResource(R.string.action_remove_file))
                    }
                }
                androidx.compose.material3.TextButton(onClick = onRemove) {
                    Text(
                        text = stringResource(R.string.action_remove),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
