package com.luread.app.ui.reader

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luread.app.AppContainer
import com.luread.app.R
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
    private val bookId: String,
    private val container: AppContainer,
) : ViewModel() {

    enum class Mode { EPUB, PDF, TEXT, HTML, CBZ }

    private val _ui = MutableStateFlow<ReaderUi>(ReaderUi.Loading)
    val ui: StateFlow<ReaderUi> = _ui.asStateFlow()

    private val _epub = MutableStateFlow<EpubUi>(EpubUi.Loading)
    val epub: StateFlow<EpubUi> = _epub.asStateFlow()

    /**
     * Teks sumber mode TEXT (digabung dari hasil baca [TxtDocument]).
     * LUREAD-FIX: pembaca memaginasi teks ini sesuai layar (lihat
     * [TextPager]) sehingga tidak ada gulir vertikal.
     */
    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    private val _pages = MutableStateFlow<List<String>>(emptyList())
    val pages: StateFlow<List<String>> = _pages.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    // ------------------------------------------------------------------
    // Auto-translate state (inline, tanpa pindah layar)
    // ------------------------------------------------------------------
    sealed interface TranslateUi {
        data object Idle : TranslateUi
        data object Loading : TranslateUi
        data class Done(val original: String, val translated: String, val lang: String?) : TranslateUi
        data class Failed(val message: String) : TranslateUi
    }

    private val _translate = MutableStateFlow<TranslateUi>(TranslateUi.Idle)
    val translate: StateFlow<TranslateUi> = _translate.asStateFlow()

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
                Mode.CBZ -> openCbz(loaded)
                Mode.TEXT -> loadText(loaded)
                else -> Unit
            }
        }

        viewModelScope.launch {
            progressEvents.debounce(1_200).collect { (progress, location) ->
                container.library.saveProgress(bookId, progress, location)
            }
        }

        // LUREAD-FEAT: pengaturan huruf/arah baca langsung diterapkan ke
        // EPUB yang sedang terbuka tanpa membuka ulang buku.
        viewModelScope.launch {
            settings.collect { s ->
                val ready = _epub.value as? EpubUi.Ready ?: return@collect
                val controller = ready.state.controller ?: return@collect
                controller.preferences = readerPreferences(s.clampedFontSize, s)
            }
        }
    }

    fun detectMode(target: BookEntity): Mode {
        val path = target.localPath.orEmpty()
        val contentType = target.contentType.orEmpty()
        return when {
            contentType == Formats.MIME_EPUB || path.endsWith(".epub", ignoreCase = true) -> Mode.EPUB
            contentType == Formats.MIME_CBZ || path.endsWith(".cbz", ignoreCase = true) ||
                path.endsWith(".cbr", ignoreCase = true) -> Mode.CBZ
            contentType == Formats.MIME_PDF || path.endsWith(".pdf", ignoreCase = true) -> Mode.PDF
            contentType == Formats.MIME_HTML ||
                path.endsWith(".htm", ignoreCase = true) -> Mode.HTML
            else -> Mode.TEXT
        }
    }

    // ------------------------------------------------------------------
    // Auto-translate inline (tanpa pindah layar)
    // ------------------------------------------------------------------

    /**
     * Terjemahkan [text] secara inline memakai TranslationService (failover
     * Google gtx -> MyMemory). Hasil ditampilkan lewat [translate].
     *
     * Teks panjang dipotong per [CHUNK_TRANSLATE_CHARS] dan diterjemahkan
     * berurutan sampai selesai, lalu digabung menjadi terjemahan penuh.
     */
    fun translateInline(text: String) {
        if (text.isBlank()) {
            _translate.value = TranslateUi.Failed(
                container.appContext.getString(R.string.reader_select_text_hint),
            )
            return
        }
        _translate.value = TranslateUi.Loading
        viewModelScope.launch {
            val target = settings.value.defaultTranslateLanguage.ifBlank { "id" }
            // Pecah per blok kalimat agar tidak melebihi batas provider.
            val chunks = splitForTranslation(text, CHUNK_TRANSLATE_CHARS)
            val results = mutableListOf<String>()
            var detectedLang: String? = null
            for ((i, chunk) in chunks.withIndex()) {
                when (val outcome = container.translator.translate(chunk, "auto", target)) {
                    is com.luread.app.data.translate.TranslationService.Outcome.Success -> {
                        results.add(outcome.result.translatedText)
                        if (detectedLang == null) {
                            detectedLang = outcome.result.detectedSourceLang
                        }
                    }
                    is com.luread.app.data.translate.TranslationService.Outcome.Failure -> {
                        // Chunk gagal: sisa teks ditandai gagal.
                        val partial = results.joinToString("")
                        _translate.value = TranslateUi.Done(
                            original = text,
                            translated = partial + "\n\n[Chunk ${i + 1}/${chunks.size} gagal: ${outcome.message}]",
                            lang = detectedLang,
                        )
                        return@launch
                    }
                }
            }
            _translate.value = TranslateUi.Done(
                original = text,
                translated = results.joinToString(""),
                lang = detectedLang,
            )
        }
    }

    /**
     * Pecah [text] menjadi potongan ≤ [maxChars] di batas kalimat/kalimat
     * sehingga terjemahan per blok tidak terpotong di tengah kata.
     */
    private fun splitForTranslation(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val chunks = mutableListOf<String>()
        var remaining = text
        while (remaining.length > maxChars) {
            // Cari batas kalimat terakhir dalam window.
            val window = remaining.substring(0, maxChars)
            val lastPeriod = window.lastIndexOf(". ")
            val lastNewline = window.lastIndexOf('\n')
            val cut = maxOf(lastPeriod + 1, lastNewline + 1, maxChars / 2)
            chunks.add(remaining.substring(0, cut).trim())
            remaining = remaining.substring(cut).trimStart()
        }
        if (remaining.isNotBlank()) chunks.add(remaining)
        return chunks.filter { it.isNotBlank() }
    }

    /** Ambil teks halaman yang sedang dibaca (mode TEXT) untuk diterjemahkan. */
    fun translateCurrentTextPage() {
        val page = savedPage()
        val text = _pages.value.getOrNull(page)
        if (text.isNullOrBlank()) {
            _translate.value = TranslateUi.Failed(
                container.appContext.getString(R.string.reader_select_text_hint),
            )
            return
        }
        translateInline(text)
    }

    fun clearTranslate() {
        _translate.value = TranslateUi.Idle
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
            fontSp = settings.value.clampedFontSize,
            userSettings = settings.value,
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

    /**
     * CBZ/CBR (comic book ZIP/RAR): ekstrak gambar ke cache lalu tampilkan
     * sebagai pager gambar. RAR tidak diekstrak (butuh library tambahan);
     * hanya ZIP yang didukung.
     */
    private suspend fun openCbz(target: BookEntity) {
        val path = target.localPath
        if (path.isNullOrBlank()) {
            _ui.value = ReaderUi.Failed(container.appContext.getString(R.string.reader_error_no_text_file))
            return
        }
        withContext(Dispatchers.IO) {
            runCatching {
                val cacheDir = java.io.File(container.appContext.cacheDir, "cbz_${bookId.hashCode()}")
                cacheDir.mkdirs()
                val existing = cacheDir.listFiles()?.filter {
                    it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif")
                }?.sortedBy { it.name }
                if (existing != null && existing.isNotEmpty()) {
                    _cbzImages.value = existing.map { it.absolutePath }
                    return@withContext
                }
                java.util.zip.ZipFile(path).use { zip ->
                    val entries = zip.entries().toList()
                        .filter { !it.isDirectory && it.name.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif") }
                        .sortedBy { it.name }
                    entries.forEach { entry ->
                        val out = java.io.File(cacheDir, entry.name.substringAfterLast('/'))
                        zip.getInputStream(entry).use { input ->
                            out.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                    _cbzImages.value = entries.map { java.io.File(cacheDir, it.name.substringAfterLast('/')).absolutePath }
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                _ui.value = ReaderUi.Failed(
                    error.message ?: container.appContext.getString(R.string.reader_error_read_text),
                )
            }
        }
    }

    private val _cbzImages = MutableStateFlow<List<String>>(emptyList())
    val cbzImages: StateFlow<List<String>> = _cbzImages.asStateFlow()

    /**
     * Preferensi EPUB.
     *
     * LUREAD-FIX: `scroll = false` mematikan gulir vertikal — Readium
     * memakai paginasi sintetis (geser kiri/kanan) pada `scroll=false`.
     * Arah baca mengikuti pengaturan: umumnya Kiri→Kanan (LTR), mode
     * kanan-ke-kiri bila pengguna memilih RTL. Ukuran huruf, jarak baris,
     * jenis huruf, dan perataan ikut pengaturan pembaca.
     */
    private fun readerPreferences(fontSp: Float, userSettings: UserSettings): ReflowableWebPreferences {
        // Latar belakang baca DIPAKSA putih (terang) — permintaan mutlak user.
        // `overridePublisherColors = true` memaksa warna teks/latar dari
        // preferensi ini mengalahkan gaya bawaan publisher (banyak EPUB
        // Gutenberg punya latar gelap).
        val base = ReflowableWebPreferences.LightTheme
        val family = when (userSettings.fontFamily) {
            "sans" -> org.readium.r2.navigator.preferences.FontFamily.SANS_SERIF
            "monospace" -> org.readium.r2.navigator.preferences.FontFamily.MONOSPACE
            "cursive" -> org.readium.r2.navigator.preferences.FontFamily.CURSIVE
            "fantasy" -> org.readium.r2.navigator.preferences.FontFamily.FANTASY
            else -> org.readium.r2.navigator.preferences.FontFamily.SERIF
        }
        return base.copy(
            scroll = false,
            readingProgression = if (userSettings.isRtlReading) {
                org.readium.r2.navigator.preferences.ReadingProgression.RTL
            } else {
                org.readium.r2.navigator.preferences.ReadingProgression.LTR
            },
            fontSize = (fontSp / 16.0).coerceIn(0.75, 2.5),
            lineHeight = userSettings.clampedLineHeight.toDouble(),
            fontFamily = family,
            textAlign = if (userSettings.justifyText) {
                org.readium.r2.navigator.preferences.TextAlign.JUSTIFY
            } else {
                org.readium.r2.navigator.preferences.TextAlign.START
            },
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
        _text.value = ""
        withContext(Dispatchers.IO) {
            runCatching {
                com.luread.app.core.util.TxtDocument.load(File(path)).pages.joinToString("")
            }
                .onSuccess { _text.value = it }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _ui.value = ReaderUi.Failed(
                        error.message ?: container.appContext.getString(R.string.reader_error_read_text),
                    )
                }
        }
    }

    /**
     * Daftarkan halaman visual hasil paginasi layar (mode TEXT) sehingga
     * fitur "terjemahkan halaman ini" membaca teks halaman yang benar.
     */
    fun onVisualPages(pages: List<String>) {
        _pages.value = pages
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
        /** Batas karakter per chunk terjemahan ( Google gtx limit ~5000). */
        const val CHUNK_TRANSLATE_CHARS = 4000

        fun decodeLocation(json: String?): SavedLocation? = LocationCodec.decode(json)

        fun encodeLocation(location: SavedLocation): String = LocationCodec.encode(location)
    }
}
