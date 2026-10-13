package com.luread.app.ui.reader

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luread.app.AppContainer
import com.luread.app.R
import com.luread.app.core.design.ThemeMode
import com.luread.app.data.catalog.Formats
import com.luread.app.data.db.AnnotationEntity
import com.luread.app.data.db.BookEntity
import com.luread.app.data.settings.UserSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.navigator.common.Progression
import org.readium.navigator.web.reflowable.ReflowableWebGoLocation
import org.readium.navigator.web.reflowable.ReflowableWebRenditionFactory
import org.readium.navigator.web.reflowable.ReflowableWebRenditionState
import org.readium.navigator.web.reflowable.preferences.ReflowableWebPreferences
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File

sealed interface ReaderUi {
    data object Loading : ReaderUi
    data class Ready(val book: BookEntity, val mode: ReaderViewModel.Mode) : ReaderUi
    data class Failed(val message: String) : ReaderUi
}

sealed interface EpubUi {
    data object Loading : EpubUi
    data class Ready(
        val publication: org.readium.r2.shared.publication.Publication,
        val state: ReflowableWebRenditionState,
    ) : EpubUi

    data class Failed(val message: String) : EpubUi
}

/**
 * ViewModel pembaca.
 *
 * Bertanggung jawab atas: memuat buku, memilih mesin baca yang sesuai format,
 * membuka publikasi EPUB lewat Readium, menyimpan progres & sesi, serta
 * anotasi (penanda dan sorotan).
 */
@OptIn(ExperimentalReadiumApi::class)
class ReaderViewModel(
    private val bookId: Int,
    private val container: AppContainer,
) : ViewModel() {

    enum class Mode { EPUB, PDF, TEXT, HTML }

    private val _ui = MutableStateFlow<ReaderUi>(ReaderUi.Loading)
    val ui: StateFlow<ReaderUi> = _ui.asStateFlow()

    private val _epub = MutableStateFlow<EpubUi>(EpubUi.Loading)
    val epub: StateFlow<EpubUi> = _epub.asStateFlow()

    private val _pages = MutableStateFlow<List<String>>(emptyList())
    val pages: StateFlow<List<String>> = _pages.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    val settings: StateFlow<UserSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    /** Penanda dan sorotan untuk buku ini. */
    val annotationList: StateFlow<List<AnnotationEntity>> =
        container.library.observeAnnotations(bookId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var book: BookEntity? = null
    private var startedAt: Long = System.currentTimeMillis()
    private var progressBefore: Float = 0f
    private var lastProgress: Float = 0f
    private var lastLocation: String? = null

    private val progressEvents = MutableSharedFlow<Pair<Float, String?>>()

    init {
        viewModelScope.launch {
            val loaded = container.library.book(bookId)
            if (loaded == null) {
                _ui.value = ReaderUi.Failed(
                    container.appContext.getString(R.string.reader_error_book_missing),
                )
                return@launch
            }
            book = loaded
            progressBefore = loaded.progress
            lastProgress = loaded.progress
            lastLocation = loaded.locationJson
            _progress.value = loaded.progress
            startedAt = System.currentTimeMillis()

            val mode = detectMode(loaded)
            _ui.value = ReaderUi.Ready(loaded, mode)

            when (mode) {
                Mode.EPUB -> openEpub(loaded)
                Mode.TEXT -> loadText(loaded)
                else -> Unit
            }
        }

        viewModelScope.launch {
            progressEvents.debounce(1_200).collect { (progress, location) ->
                container.library.saveProgress(bookId, progress, location)
            }
        }
    }

    fun detectMode(target: BookEntity): Mode {
        val path = target.localPath.orEmpty()
        val contentType = target.contentType.orEmpty()
        return when {
            contentType == Formats.MIME_EPUB || path.endsWith(".epub", ignoreCase = true) -> Mode.EPUB
            contentType == Formats.MIME_PDF || path.endsWith(".pdf", ignoreCase = true) -> Mode.PDF
            contentType == Formats.MIME_HTML ||
                path.endsWith(".htm", ignoreCase = true) -> Mode.HTML
            else -> Mode.TEXT
        }
    }

    // ------------------------------------------------------------------
    // EPUB via Readium
    // ------------------------------------------------------------------

    private suspend fun openEpub(target: BookEntity) {
        val path = target.localPath
        if (path.isNullOrBlank()) {
                _epub.value = EpubUi.Failed(
                    container.appContext.getString(R.string.reader_error_epub_missing),
                )
            return
        }

        _epub.value = EpubUi.Loading
        val app: Application = container.appContext as Application
        val prefs = readerPreferences(
            dark = settings.value.themeMode == ThemeMode.DARK,
            fontSp = settings.value.clampedFontSize,
        )
        withContext(Dispatchers.IO) {
            runCatching {
                val httpClient = DefaultHttpClient()
                val assetRetriever = AssetRetriever(app.contentResolver, httpClient)
                val opener = PublicationOpener(
                    publicationParser = DefaultPublicationParser(
                        app,
                        assetRetriever = assetRetriever,
                        httpClient = httpClient,
                        pdfFactory = null,
                    ),
                    contentProtections = emptyList(),
                )

                val asset = assetRetriever.retrieve(File(path)).getOrElse {
                    throw ReaderException(
                        container.appContext.getString(R.string.reader_error_open_file, it.message),
                    )
                }
                val publication = opener.open(asset, allowUserInteraction = false).getOrElse {
                    throw ReaderException(
                        container.appContext.getString(R.string.reader_error_unsupported_epub, it.message),
                    )
                }

                val factory = ReflowableWebRenditionFactory(
                    application = app,
                    publication = publication,
                ) ?: throw ReaderException(container.appContext.getString(R.string.reader_error_not_reflowable))

                val saved = decodeLocation(target.locationJson)
                val initialLocation = saved?.let { s ->
                    val href = Url(s.href) ?: return@let null
                    ReflowableWebGoLocation(
                        href = href,
                        progression = s.progression?.let { p -> Progression(p) },
                    )
                }

                val state = factory.createRenditionState(
                    initialPreferences = prefs,
                    initialLocation = initialLocation,
                ).getOrElse {
                    throw ReaderException(
                        container.appContext.getString(R.string.reader_error_prepare, it.message),
                    )
                }

                publication to state
            }.fold(
                onSuccess = { (publication, state) ->
                    _epub.value = EpubUi.Ready(publication, state)
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _epub.value = EpubUi.Failed(
                        error.message ?: container.appContext.getString(R.string.reader_error_epub),
                    )
                },
            )
        }
    }

    private fun readerPreferences(dark: Boolean, fontSp: Float): ReflowableWebPreferences {
        val base = if (dark) {
            ReflowableWebPreferences.DarkTheme
        } else {
            ReflowableWebPreferences.LightTheme
        }
        return base.copy(
            scroll = true,
            fontSize = (fontSp / 16.0).coerceIn(0.75, 2.5),
            overridePublisherColors = true,
        )
    }

    /** Dipanggil pembaca EPUB setiap kali lokasi berubah. */
    fun onEpubLocation(progress: Double, href: String, text: String? = null) {
        val clamped = progress.coerceIn(0.0, 1.0)
        onProgress(clamped.toFloat(), encodeLocation(SavedLocation(href = href, progression = clamped, text = text)))
    }

    fun currentSettings(): UserSettings = settings.value

    // ------------------------------------------------------------------
    // Teks & HTML
    // ------------------------------------------------------------------

    private suspend fun loadText(target: BookEntity) {
        val path = target.localPath
        if (path.isNullOrBlank()) {
            _ui.value = ReaderUi.Failed(container.appContext.getString(R.string.reader_error_no_text_file))
            return
        }
        _pages.value = emptyList()
        withContext(Dispatchers.IO) {
            runCatching { com.luread.app.core.util.TxtDocument.load(File(path)).pages }
                .onSuccess { _pages.value = it }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _ui.value = ReaderUi.Failed(
                        error.message ?: container.appContext.getString(R.string.reader_error_read_text),
                    )
                }
        }
    }

    // ------------------------------------------------------------------
    // Progres & sesi
    // ------------------------------------------------------------------

    fun onProgress(progress: Float, locationJson: String?) {
        lastProgress = progress.coerceIn(0f, 1f)
        lastLocation = locationJson
        _progress.value = lastProgress
        viewModelScope.launch { progressEvents.emit(lastProgress to locationJson) }
    }

    fun onProgressPage(pageIndex: Int, pageCount: Int) {
        if (pageCount <= 0) return
        val fraction = ((pageIndex + 1).toFloat() / pageCount.toFloat()).coerceIn(0f, 1f)
        onProgress(
            fraction,
            encodeLocation(SavedLocation(href = "page", page = pageIndex)),
        )
    }

    fun savedPage(): Int = decodeLocation(lastLocation)?.page ?: 0

    /**
     * Dipanggil saat pembaca ditutup: simpan progres dan catat sesi baca
     * (sesi hanya dicatat bila pengguna mengaktifkan statistik).
     */
    fun onLeave() {
        val snapshotBook = book ?: return
        val before = progressBefore
        val after = lastProgress
        val started = startedAt
        val location = lastLocation
        val trackStats = settings.value.trackReadingStats

        val scope = container.appScope
        scope.launch {
            container.library.saveProgress(bookId, after, location)
            if (trackStats) {
                container.library.recordSession(
                    bookId = bookId,
                    bookTitle = snapshotBook.title,
                    progressBefore = before,
                    progressAfter = after,
                    startedAt = started,
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // Anotasi
    // ------------------------------------------------------------------

    fun addBookmark(text: String, href: String?, location: String?) {
        viewModelScope.launch {
            container.library.addAnnotation(
                bookId = bookId,
                kind = AnnotationEntity.Kind.BOOKMARK,
                text = text.ifBlank { container.appContext.getString(R.string.annotation_default) },
                href = href,
                locationJson = location,
            )
            _message.value = container.appContext.getString(R.string.msg_bookmark_saved)
        }
    }

    fun addHighlight(text: String, href: String?, location: String?) {
        viewModelScope.launch {
            container.library.addAnnotation(
                bookId = bookId,
                kind = AnnotationEntity.Kind.HIGHLIGHT,
                text = text,
                href = href,
                locationJson = location,
            )
            _message.value = container.appContext.getString(R.string.msg_highlight_saved)
        }
    }

    fun deleteAnnotation(id: Long) {
        viewModelScope.launch { container.library.deleteAnnotation(id) }
    }

    /**
     * Lompat ke lokasi anotasi. Hanya EPUB yang mendukungnya karena format
     * lain menyimpan posisi sebagai nomor halaman yang sudah lewat.
     *
     * @return true bila lompatan dilakukan.
     */
    suspend fun jumpTo(annotation: AnnotationEntity): Boolean {
        val ready = _epub.value as? EpubUi.Ready ?: return false
        val location = decodeLocation(annotation.locationJson) ?: return false
        val href = Url(location.href) ?: return false
        val controller = ready.state.controller ?: return false
        controller.goTo(
            ReflowableWebGoLocation(
                href = href,
                progression = location.progression?.let { p -> Progression(p) },
            ),
        )
        return true
    }

    fun clearMessage() {
        _message.value = null
    }

    /** Tampilkan pesan singkat di pembaca (misal saat belum ada teks terpilih). */
    fun notify(text: String) {
        _message.value = text
    }

    class ReaderException(message: String) : Exception(message)

    companion object {
        fun decodeLocation(json: String?): SavedLocation? = LocationCodec.decode(json)

        fun encodeLocation(location: SavedLocation): String = LocationCodec.encode(location)
    }
}
