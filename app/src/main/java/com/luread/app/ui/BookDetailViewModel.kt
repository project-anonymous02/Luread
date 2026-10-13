package com.luread.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luread.app.data.network.CatalogQuery
import com.luread.app.data.catalog.CatalogRepository
import com.luread.app.data.catalog.Formats
import com.luread.app.data.db.BookEntity
import com.luread.app.data.downloads.DownloadManager
import com.luread.app.data.library.LibraryRepository
import com.luread.app.data.network.ApiResult
import com.luread.app.data.network.GutendexBook
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Model tampilan detail buku yang menggabungkan snapshot lokal dan data katalog. */
data class BookDetailUi(
    val id: Int,
    val title: String,
    val authorsText: String,
    val summary: String?,
    val subjectsText: String,
    val languagesText: String,
    val coverUrl: String?,
    val inLibrary: Boolean,
    val localPath: String?,
    val contentType: String?,
    val progress: Float,
    val finished: Boolean,
    val favorite: Boolean,
    val downloadState: BookEntity.DownloadState,
    val downloadProgress: DownloadManager.Progress?,
    val formats: List<Formats.AvailableFormat>,
    val downloadCount: Int?,
    val copyrightStatus: Boolean?,
    val canRead: Boolean,
    val canDownload: Boolean,
)

/**
 * Detail buku.
 *
 * Sumber data:
 *  * snapshot lokal bila buku sudah masuk perpustakaan (jalan offline);
 *  * `GET /books/?ids=<id>` bila belum (parameter ini sudah diuji 200 di audit);
 *  * kegagalan katalog pada buku yang sudah tersimpan lokal tetap menampilkan
 *    layar dengan data snapshot.
 */
class BookDetailViewModel(
    private val bookId: Int,
    private val library: LibraryRepository,
    private val catalog: CatalogRepository,
    private val downloads: DownloadManager,
) : ViewModel() {

    private val catalogBook = MutableStateFlow<GutendexBook?>(null)
    private val catalogError = MutableStateFlow<String?>(null)
    private val catalogLoading = MutableStateFlow(false)

    private val libraryBook: StateFlow<BookEntity?> = library.observeBook(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val ui: StateFlow<BookDetailUi?> = combine(
        libraryBook,
        catalogBook,
        downloads.progress,
        catalogError,
    ) { local, remote, progressMap, error ->
        if (local == null && remote == null && error != null) return@combine null
        merge(local, remote, progressMap[bookId])
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isLoading: StateFlow<Boolean> = catalogLoading
        .combine(libraryBook) { loading, local -> loading && local == null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    init {
        refresh()
    }

    fun refresh() {
        if (libraryBook.value != null) {
            // Snapshot lokal cukup; katalog hanya pelengkap.
            fetchCatalog(force = false)
        } else {
            fetchCatalog(force = true)
        }
    }

    private fun fetchCatalog(force: Boolean) {
        if (catalogBook.value != null && !force) return
        catalogLoading.value = true
        viewModelScope.launch {
            when (val result = catalog.firstPage(CatalogQuery(ids = bookId))) {
                is ApiResult.Success -> {
                    val found = result.value.results.firstOrNull { it.id == bookId }
                        ?: result.value.results.firstOrNull()
                    if (found != null) {
                        catalogBook.value = found
                        catalogError.value = null
                        // Pastikan buku yang dibuka dari luar tersedia di perpustakaan
                        // hanya jika pengguna menambahkannya; tidak dilakukan otomatis.
                    } else {
                        catalogError.value = "Book #$bookId was not found in the catalogue."
                    }
                }
                is ApiResult.Failure -> {
                    if (libraryBook.value == null) {
                        catalogError.value = result.error.toUserMessage()
                    }
                }
            }
            catalogLoading.value = false
        }
    }

    fun addToLibrary() {
        val remote = catalogBook.value ?: return
        viewModelScope.launch { library.addFromCatalog(remote) }
    }

    fun removeFromLibrary() {
        viewModelScope.launch { library.remove(bookId) }
    }

    fun toggleFavorite() {
        val current = libraryBook.value ?: return
        viewModelScope.launch { library.setFavorite(bookId, !current.favorite) }
    }

    fun download() {
        val url = libraryBook.value?.sourceUrl
            ?: Formats.urlsByMime(catalogBook.value?.formats ?: emptyMap()).let { map ->
                Formats.bestReadable(catalogBook.value?.formats ?: emptyMap())
                    ?.let { readable -> map[readable.mime] }
            }
            ?: return
        val mime = libraryBook.value?.contentType
            ?: Formats.bestReadable(catalogBook.value?.formats ?: emptyMap())?.mime
        downloads.enqueue(bookId, url, mime)
        if (libraryBook.value == null) {
            val remote = catalogBook.value
            if (remote != null) viewModelScope.launch { library.addFromCatalog(remote) }
        }
    }

    fun cancelDownload() = downloads.cancel(bookId)

    fun deleteLocalFile() {
        val path = libraryBook.value?.localPath ?: return
        downloads.deleteFile(path)
        viewModelScope.launch {
            library.clearLocalFile(bookId)
        }
    }

    private fun merge(
        local: BookEntity?,
        remote: GutendexBook?,
        downloadProgress: DownloadManager.Progress?,
    ): BookDetailUi? {
        val readableRemote = remote?.let { Formats.bestReadable(it.formats) }
        val remoteUrls = remote?.let { Formats.urlsByMime(it.formats) }.orEmpty()
        val formats = remote?.formats?.let { Formats.available(it) } ?: emptyList()
        val downloadState = local?.downloadState?.let {
            runCatching { BookEntity.DownloadState.valueOf(it) }.getOrDefault(BookEntity.DownloadState.NONE)
        } ?: BookEntity.DownloadState.NONE

        val title = local?.title ?: remote?.title ?: return null
        val sourceUrl = local?.sourceUrl
            ?: readableRemote?.let { remoteUrls[it.mime] }
        val contentType = local?.contentType ?: readableRemote?.mime
        val localPath = local?.localPath

        return BookDetailUi(
            id = bookId,
            title = title,
            authorsText = local?.authorsText?.takeIf { it.isNotBlank() }
                ?: remote?.authorNames?.joinToString(", ").orEmpty(),
            summary = local?.summary ?: remote?.summary,
            subjectsText = local?.subjectsText?.takeIf { it.isNotBlank() }
                ?: remote?.subjects?.take(8)?.joinToString(", ").orEmpty(),
            languagesText = local?.languagesText?.takeIf { it.isNotBlank() }
                ?: remote?.languages?.joinToString(", ").orEmpty(),
            coverUrl = local?.coverUrl ?: remote?.let { Formats.coverUrl(it.formats) },
            inLibrary = local != null,
            localPath = localPath,
            contentType = contentType,
            progress = local?.progress ?: 0f,
            finished = local?.finished ?: false,
            favorite = local?.favorite ?: false,
            downloadState = downloadState,
            downloadProgress = downloadProgress,
            formats = formats,
            downloadCount = remote?.downloadCount,
            copyrightStatus = remote?.copyright,
            canRead = localPath != null,
            canDownload = sourceUrl != null && localPath == null &&
                downloadState != BookEntity.DownloadState.RUNNING,
        )
    }

    val error: StateFlow<String?> = catalogError
}
