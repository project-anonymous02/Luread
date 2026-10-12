package com.luread.app.data.library

import com.luread.app.data.db.AnnotationEntity
import com.luread.app.data.db.BookDao
import com.luread.app.data.db.BookEntity
import com.luread.app.data.db.SessionDao
import com.luread.app.data.db.SessionEntity
import com.luread.app.data.network.GutendexBook
import com.luread.app.data.catalog.Formats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Calendar

/**
 * Akses data perpustakaan, riwayat membaca, dan anotasi.
 *
 * Semua data disimpan lebih dulu di Room; sumber katalog hanya dipakai untuk
 * membuat snapshot saat buku ditambahkan, sehingga layar tetap berguna saat
 * perangkat offline.
 */
class LibraryRepository(
    private val books: BookDao,
    private val sessions: SessionDao,
    private val annotations: com.luread.app.data.db.AnnotationDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeLibrary(): Flow<List<BookEntity>> = books.observeAll()

    fun observeDownloaded(): Flow<List<BookEntity>> = books.observeDownloaded()

    fun observeBook(id: Int): Flow<BookEntity?> = books.observeById(id)

    suspend fun book(id: Int): BookEntity? = books.byId(id)

    suspend fun downloadedBooks(): List<BookEntity> = books.downloaded()

    /** Menyimpan snapshot buku dari katalog ke perpustakaan. */
    suspend fun addFromCatalog(book: GutendexBook): BookEntity {
        val cover = Formats.coverUrl(book.formats)
        val readable = Formats.bestReadable(book.formats)
        val entity = BookEntity(
            id = book.id,
            title = book.title.ifBlank { "Untitled" },
            authorsText = book.authorNames.joinToString(", "),
            summary = book.summary,
            languagesText = book.languages.joinToString(", "),
            subjectsText = book.subjects.take(6).joinToString(", "),
            coverUrl = cover,
            sourceUrl = readable?.let { Formats.urlsByMime(book.formats)[it.mime] },
            contentType = readable?.mime,
            localPath = null,
            downloadState = BookEntity.DownloadState.NONE.name,
            addedAt = clock(),
        )
        books.upsert(entity)
        return entity
    }

    /** Tambahkan berkas terimpor (dari SAF) tanpa sumber katalog. */
    suspend fun addImported(
        id: Int,
        title: String,
        authorsText: String,
        path: String,
        mime: String,
        coverUrl: String? = null,
    ): BookEntity {
        val entity = BookEntity(
            id = id,
            title = title,
            authorsText = authorsText,
            summary = null,
            languagesText = "",
            subjectsText = "",
            coverUrl = coverUrl,
            sourceUrl = null,
            contentType = mime,
            localPath = path,
            downloadState = BookEntity.DownloadState.DONE.name,
            addedAt = clock(),
            lastOpenedAt = clock(),
        )
        books.upsert(entity)
        return entity
    }

    suspend fun setFavorite(id: Int, favorite: Boolean) = books.setFavorite(id, favorite)

    suspend fun updateDownloadState(id: Int, state: BookEntity.DownloadState, done: Long = 0L, total: Long = 0L) =
        books.updateDownload(id, state.name, done, total)

    suspend fun setLocalFile(id: Int, path: String, mime: String) =
        books.setLocalFile(id, path, mime, BookEntity.DownloadState.DONE.name)

    /** Hapus berkas lokal tanpa menghapus entri perpustakaan. */
    suspend fun clearLocalFile(id: Int) =
        books.clearLocalFile(id, BookEntity.DownloadState.NONE.name)

    suspend fun remove(id: Int) {
        books.delete(id)
        annotations.deleteForBook(id)
    }

    /**
     * Menyimpan progres baca. Buku dianggap selesai bila progres >= 99%.
     */
    suspend fun saveProgress(id: Int, progress: Float, locationJson: String?) {
        val clamped = progress.coerceIn(0f, 1f)
        books.updateReadingState(
            id = id,
            progress = clamped,
            locationJson = locationJson,
            openedAt = clock(),
            finished = clamped >= 0.99f,
        )
    }

    // ------------------------------------------------------------------
    // Riwayat aktivitas
    // ------------------------------------------------------------------

    fun observeActivity(): Flow<List<SessionEntity>> = sessions.observeRecent()

    suspend fun recordSession(
        bookId: Int,
        bookTitle: String,
        progressBefore: Float,
        progressAfter: Float,
        startedAt: Long,
        endedAt: Long = clock(),
    ) = sessions.insert(bookId, bookTitle, startedAt, endedAt, progressBefore, progressAfter)

    suspend fun clearActivity() = sessions.clear()

    // ------------------------------------------------------------------
    // Anotasi
    // ------------------------------------------------------------------

    fun observeAnnotations(bookId: Int): Flow<List<AnnotationEntity>> = annotations.observeForBook(bookId)

    fun observeAllAnnotations(): Flow<List<AnnotationEntity>> = annotations.observeAll()

    suspend fun addAnnotation(
        bookId: Int,
        kind: AnnotationEntity.Kind,
        text: String,
        href: String? = null,
        note: String = "",
        locationJson: String? = null,
    ): Long {
        val now = clock()
        return annotations.upsert(
            AnnotationEntity(
                bookId = bookId,
                href = href,
                text = text,
                note = note,
                kind = kind.name,
                locationJson = locationJson,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun deleteAnnotation(id: Long) = annotations.delete(id)

    // ------------------------------------------------------------------
    // Statistik
    // ------------------------------------------------------------------

    data class ReadingStats(
        val libraryCount: Int = 0,
        val downloadedCount: Int = 0,
        val finishedCount: Int = 0,
        val todayMillis: Long = 0L,
        val totalMillis: Long = 0L,
    )

    fun observeStats(): Flow<ReadingStats> =
        combine(
            books.observeAll(),
            books.observeDownloadedCount(),
            sessions.observeMillisSince(startOfToday()),
            sessions.observeRecent(1000),
        ) { all, downloaded, todayMillis, recent ->
            ReadingStats(
                libraryCount = all.size,
                downloadedCount = downloaded,
                finishedCount = all.count { it.finished },
                todayMillis = todayMillis,
                totalMillis = recent.sumOf { it.durationMillis },
            )
        }

    fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun observeRecentBooks(limit: Int = 6): Flow<List<BookEntity>> =
        observeLibrary().map { list -> list.filter { it.lastOpenedAt > 0L }.take(limit) }
}
