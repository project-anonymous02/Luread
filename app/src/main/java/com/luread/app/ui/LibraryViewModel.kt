package com.luread.app.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luread.app.R
import com.luread.app.data.catalog.Formats
import com.luread.app.data.db.BookEntity
import com.luread.app.data.library.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** Perpustakaan: daftar koleksi, filter, dan impor file dari penyimpanan perangkat. */
class LibraryViewModel(
    private val library: LibraryRepository,
    private val appContext: Context,
) : ViewModel() {

    enum class Filter(@StringRes val labelRes: Int) {
        ALL(R.string.library_filter_all),
        DOWNLOADED(R.string.library_filter_downloaded),
        FINISHED(R.string.library_filter_finished),
        FAVORITE(R.string.library_filter_favorite),
    }

    private val filter = MutableStateFlow(Filter.ALL)
    val filterState: StateFlow<Filter> = filter

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    val books: StateFlow<List<BookEntity>> =
        combine(library.observeLibrary(), filter) { list, selected ->
            when (selected) {
                Filter.ALL -> list
                Filter.DOWNLOADED -> list.filter { it.localPath != null }
                Filter.FINISHED -> list.filter { it.finished }
                Filter.FAVORITE -> list.filter { it.favorite }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalCount: StateFlow<Int> = library.observeLibrary()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val stats: StateFlow<LibraryRepository.ReadingStats> = library.observeStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryRepository.ReadingStats())

    fun setFilter(value: Filter) {
        filter.value = value
    }

    fun dismissMessage() {
        _message.value = null
    }

    fun remove(id: String) {
        viewModelScope.launch { library.remove(id) }
    }

    fun toggleFavorite(id: String, favorite: Boolean) {
        viewModelScope.launch { library.setFavorite(id, favorite) }
    }

    /**
     * Impor file EPUB/PDF/HTML/TXT yang dipilih lewat SAF.
     *
     * File disalin ke penyimpanan internal aplikasi (bukan URI asli) sehingga
     * dibaca konsisten oleh Readium dan PdfRenderer. Format divalidasi dengan
     * magic number, bukan hanya ekstensi nama file.
     */
    fun import(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val resolver = appContext.contentResolver
                val displayName = resolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                } ?: "imported"

                val nameBase = displayName.substringBeforeLast('.', displayName)
                val nameExt = displayName.substringAfterLast('.', "").lowercase()

                var key = "local:${System.currentTimeMillis()}"
                var attempt = 0
                while (library.book(key) != null) key = "local:${System.currentTimeMillis()}-${attempt++}"

                val magic = resolver.openInputStream(uri)?.use { input ->
                    val head = ByteArray(8)
                    var read = 0
                    while (read < head.size) {
                        val n = input.read(head, read, head.size - read)
                        if (n < 0) break
                        read += n
                    }
                head.copyOf(read)
            } ?: throw IllegalArgumentException(appContext.getString(R.string.import_error_open))

            val sniffed = Formats.sniff(magic) ?: when (nameExt) {
                "epub" -> Formats.MIME_EPUB
                "pdf" -> Formats.MIME_PDF
                "txt", "text" -> Formats.MIME_TEXT
                "html", "htm" -> Formats.MIME_HTML
                else -> throw IllegalArgumentException(
                    appContext.getString(R.string.import_error_format, nameExt),
                )
            }
            val mime = if (sniffed == Formats.EPUB_OR_ZIP) Formats.MIME_EPUB else sniffed

            val dir = File(appContext.filesDir, "books").apply { mkdirs() }
            val target = File(dir, "${key.replace(':', '_')}.${Formats.extensionFor(mime)}")
            val copied = resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
                target.length()
            } ?: throw IllegalArgumentException(appContext.getString(R.string.import_error_copy))
            if (copied <= 0L) {
                target.delete()
                throw IllegalArgumentException(appContext.getString(R.string.import_error_empty))
            }

            val title = nameBase.ifBlank { appContext.getString(R.string.untitled) }
            library.addImported(
                key = key,
                title = title,
                authorsText = "",
                path = target.absolutePath,
                mime = mime,
            )
            appContext.getString(R.string.import_added, title)
        }.fold(
            onSuccess = { _message.value = it },
            onFailure = { _message.value = it.message ?: appContext.getString(R.string.import_failed) },
        )
        }
    }
}
