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
        COLLECTION(R.string.library_filter_collection),
    }

    enum class Sort(@StringRes val labelRes: Int) {
        ADDED_DESC(R.string.library_sort_added_desc),
        ADDED_ASC(R.string.library_sort_added_asc),
        TITLE_ASC(R.string.library_sort_title_asc),
        TITLE_DESC(R.string.library_sort_title_desc),
        AUTHOR_ASC(R.string.library_sort_author_asc),
        PROGRESS_DESC(R.string.library_sort_progress_desc),
    }

    private val filter = MutableStateFlow(Filter.ALL)
    val filterState: StateFlow<Filter> = filter

    private val sort = MutableStateFlow(Sort.ADDED_DESC)
    val sortState: StateFlow<Sort> = sort

    private val searchQuery = MutableStateFlow("")
    val searchState: StateFlow<String> = searchQuery

    private val collectionFilter = MutableStateFlow<String?>(null)
    val collectionFilterState: StateFlow<String?> = collectionFilter

    val collectionNames: StateFlow<List<String>> = library.observeCollectionNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    val books: StateFlow<List<BookEntity>> =
        combine(
            library.observeLibrary(),
            filter,
            sort,
            searchQuery,
            collectionFilter,
        ) { list, selected, sortBy, query, collection ->
            val filtered = when (selected) {
                Filter.ALL -> list
                Filter.DOWNLOADED -> list.filter { it.localPath != null }
                Filter.FINISHED -> list.filter { it.finished }
                Filter.FAVORITE -> list.filter { it.favorite }
                Filter.COLLECTION -> list.filter { it.collectionName == collection }
            }
            val searched = if (query.isBlank()) filtered else filtered.filter { book ->
                book.title.contains(query, ignoreCase = true) ||
                    book.authorsText.contains(query, ignoreCase = true) ||
                    book.subjectsText.contains(query, ignoreCase = true)
            }
            when (sortBy) {
                Sort.ADDED_DESC -> searched.sortedByDescending { it.addedAt }
                Sort.ADDED_ASC -> searched.sortedBy { it.addedAt }
                Sort.TITLE_ASC -> searched.sortedBy { it.title.lowercase() }
                Sort.TITLE_DESC -> searched.sortedByDescending { it.title.lowercase() }
                Sort.AUTHOR_ASC -> searched.sortedBy { it.authorsText.lowercase() }
                Sort.PROGRESS_DESC -> searched.sortedByDescending { it.progress }
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

    fun setSort(value: Sort) {
        sort.value = value
    }

    fun onSearchChange(text: String) {
        searchQuery.value = text
    }

    fun setCollectionFilter(name: String?) {
        collectionFilter.value = name
    }

    fun assignCollection(id: String, name: String?) {
        viewModelScope.launch { library.setCollection(id, name) }
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
     * Impor file EPUB/PDF/HTML/TXT/CBZ atau arsip ZIP yang dipilih lewat SAF.
     *
     * File disalin ke penyimpanan internal aplikasi (bukan URI asli) sehingga
     * dibaca konsisten oleh Readium dan PdfRenderer. Format divalidasi dengan
     * magic number, bukan hanya ekstensi nama file.
     *
     * Bila file adalah ZIP polos (bukan EPUB/CBZ), arsip diekstrak dan setiap
     * file yang bisa dibaca di dalamnya diimpor satu per satu.
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

                val magic = readMagic(resolver, uri)
                    ?: throw IllegalArgumentException(appContext.getString(R.string.import_error_open))

                // ZIP polos (bukan EPUB/CBZ): ekstrak dan impor isi yang readable.
                if (nameExt == "zip" || (Formats.sniff(magic) == Formats.EPUB_OR_ZIP &&
                        nameExt !in setOf("epub", "cbz", "cbr"))
                ) {
                    return@runCatching importZipContents(uri, nameBase)
                }

                val mime = resolveMime(nameExt, magic)
                importFromStream(uri, mime, nameBase)
            }.fold(
                onSuccess = { _message.value = it },
                onFailure = { _message.value = it.message ?: appContext.getString(R.string.import_failed) },
            )
        }
    }

    /** Baca magic number (8 byte pertama) dari URI. */
    private fun readMagic(resolver: android.content.ContentResolver, uri: Uri): ByteArray? =
        resolver.openInputStream(uri)?.use { input ->
            val head = ByteArray(8)
            var read = 0
            while (read < head.size) {
                val n = input.read(head, read, head.size - read)
                if (n < 0) break
                read += n
            }
            head.copyOf(read)
        }

    /** Tentukan MIME dari ekstensi + sniff. */
    private fun resolveMime(nameExt: String, magic: ByteArray): String {
        // Untuk CBZ/CBR, ekstensi menang atas sniff (keduanya ZIP).
        if (nameExt == "cbz" || nameExt == "cbr") return Formats.MIME_CBZ
        val sniffed = Formats.sniff(magic) ?: when (nameExt) {
            "epub" -> Formats.MIME_EPUB
            "pdf" -> Formats.MIME_PDF
            "txt", "text" -> Formats.MIME_TEXT
            "html", "htm" -> Formats.MIME_HTML
            "mobi", "prc", "azw", "azw3" -> Formats.MIME_MOBI
            "fb2" -> Formats.MIME_FB2
            "djvu" -> Formats.MIME_DJVU
            "docx" -> Formats.MIME_DOCX
            "rtf" -> Formats.MIME_RTF
            "md", "markdown" -> Formats.MIME_MARKDOWN
            else -> throw IllegalArgumentException(
                appContext.getString(R.string.import_error_format, nameExt),
            )
        }
        return when (sniffed) {
            Formats.EPUB_OR_ZIP -> Formats.MIME_EPUB
            else -> sniffed
        }
    }

    /** Salin satu stream URI ke file internal, daftarkan ke library, kembalikan judul. */
    private suspend fun importFromStream(uri: Uri, mime: String, nameBase: String): String {
        val key = uniqueKey()
        val dir = File(appContext.filesDir, "books").apply { mkdirs() }
        val target = File(dir, "${key.replace(':', '_')}.${Formats.extensionFor(mime)}")
        val resolver = appContext.contentResolver
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
        return appContext.getString(R.string.import_added, title)
    }

    /**
     * Ekstrak arsip ZIP dan impor setiap file yang bisa dibaca (EPUB/HTML/TXT/PDF/CBZ).
     * File yang tidak dikenal atau rusak dilewati agar satu file jelek tidak
     * membatalkan seluruh impor.
     */
    private suspend fun importZipContents(uri: Uri, zipNameBase: String): String {
        val tempDir = File(appContext.cacheDir, "zip-import-${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val resolver = appContext.contentResolver
            val input = resolver.openInputStream(uri)
                ?: throw IllegalArgumentException(appContext.getString(R.string.import_error_open))
            var imported = 0
            var skipped = 0
            input.use { stream ->
                java.util.zip.ZipInputStream(stream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val entryName = entry.name.substringAfterLast('/')
                            val entryExt = entryName.substringAfterLast('.', "").lowercase()
                            val entryBase = entryName.substringBeforeLast('.', entryName)
                            val tempFile = File(tempDir, "entry-$imported-$skipped")
                            tempFile.outputStream().use { out -> zip.copyTo(out) }
                            val entryMagic = tempFile.inputStream().use { s ->
                                val head = ByteArray(8)
                                var read = 0
                                while (read < head.size) {
                                    val n = s.read(head, read, head.size - read)
                                    if (n < 0) break
                                    read += n
                                }
                                head.copyOf(read)
                            }
                            val mime = runCatching { resolveMime(entryExt, entryMagic) }.getOrNull()
                            // Hanya impor format yang punya reader engine.
                            val readable = mime != null && mime in READABLE_IMPORT_MIMES
                            if (readable) {
                                val key = uniqueKey()
                                val dir = File(appContext.filesDir, "books").apply { mkdirs() }
                                val target = File(dir, "${key.replace(':', '_')}.${Formats.extensionFor(mime!!)}")
                                tempFile.copyTo(target, overwrite = true)
                                if (target.length() > 0L) {
                                    library.addImported(
                                        key = key,
                                        title = entryBase.ifBlank { zipNameBase },
                                        authorsText = "",
                                        path = target.absolutePath,
                                        mime = mime,
                                    )
                                    imported++
                                } else {
                                    target.delete()
                                    skipped++
                                }
                            } else {
                                skipped++
                            }
                        }
                        entry = zip.nextEntry
                    }
                }
            }
            return when {
                imported > 0 -> appContext.getString(R.string.import_zip_added, imported, zipNameBase)
                else -> appContext.getString(R.string.import_zip_empty)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private suspend fun uniqueKey(): String {
        var key = "local:${System.currentTimeMillis()}"
        var attempt = 0
        while (library.book(key) != null) key = "local:${System.currentTimeMillis()}-${attempt++}"
        return key
    }

    companion object {
        /** MIME yang punya reader engine dan layak diimpor dari ZIP. */
        private val READABLE_IMPORT_MIMES = setOf(
            Formats.MIME_EPUB,
            Formats.MIME_HTML,
            Formats.MIME_TEXT,
            Formats.MIME_PDF,
            Formats.MIME_CBZ,
        )
    }
}
