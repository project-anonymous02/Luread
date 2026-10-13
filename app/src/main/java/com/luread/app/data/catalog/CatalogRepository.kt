package com.luread.app.data.catalog

import com.luread.app.core.util.HtmlStrip
import com.luread.app.data.network.ApiError
import com.luread.app.data.network.ApiResult
import com.luread.app.data.network.ArchiveApi
import com.luread.app.data.network.CatalogQuery
import com.luread.app.data.network.GutendexApi
import com.luread.app.data.network.OpenLibraryApi
import com.luread.app.data.network.WikisourceApi
import com.luread.app.data.network.WikisourceSearchResponse
import com.luread.app.data.network.elementText
import com.luread.app.data.network.elementTextList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Repositori katalog multi-sumber: membangun permintaan, menerapkan filter
 * yang didukung tiap sumber, mencoba ulang kegagalan yang masuk akal, dan
 * memetakan hasil ke [CatalogPage] seragam.
 *
 * Sumber dan filtornya (semua parameter diverifikasi lewat curl 2026-10-13,
 * lihat docs/book-apis-audit.md):
 *  * Gutenberg (Gutendex): search, languages, topic, mime_type + pagination
 *    URL absolut yang dicek tepercaya;
 *  * Open Library: search (`subject:fiction` bila kosong) + language
 *    ISO 639-2 + halaman `page`;
 *  * Internet Archive: `mediatype:texts` + format readable + language +
 *    `title:(…)` / `subject:fiction` + halaman `page`;
 *  * Wikisource id: search saja (`list=search`) atau browse (`list=allpages`);
 *    topic/mime/bahasa tidak berlaku (semuanya berbahasa Indonesia).
 *
 * Kebijakan percobaan ulang (berbasis audit nyata, bukan tebakan):
 *  * gagal jaringan / timeout -> 2 kali percobaan ulang dengan jeda 400ms,
 *    900ms;
 *  * HTTP >= 500 -> 1 kali percobaan ulang, lalu dilaporkan [ApiError.Http];
 *  * respons non-JSON -> tidak diulang;
 *  * pembatalan selalu dilempar kembali.
 */
class CatalogRepository(
    private val gutendex: GutendexApi,
    private val openLibrary: OpenLibraryApi,
    private val archive: ArchiveApi,
    private val wikisource: WikisourceApi,
    private val wikibooks: WikisourceApi? = null,
    private val maxRetries: Int = 2,
    private val baseDelayMs: Long = 400L,
) {
    /** Hasil detail: item siap tampil; [payload] berisi HTML Wikisource bila ada. */
    data class CatalogDetail(val item: CatalogItem, val payload: String? = null)

    // ------------------------------------------------------------------
    // Pencarian / halaman
    // ------------------------------------------------------------------

    /** Halaman pertama (default Gutenberg agar sorotan Home tetap sama). */
    suspend fun firstPage(
        query: CatalogQuery,
        source: BookSource = BookSource.GUTENDEX,
    ): ApiResult<CatalogPage> = withRetry {
        when (source) {
            BookSource.GUTENDEX -> gutendex.fetchPage(query).mapPage()

            BookSource.OPEN_LIBRARY -> when (val r = openLibrary.search(query.search, query.languages, 1)) {
                is ApiResult.Failure -> r
                is ApiResult.Success -> ApiResult.Success(
                    CatalogPage(
                        results = r.value.docs.map { it.toItem() },
                        next = cursor(page = 2, fetched = r.value.docs.size, total = r.value.numFound),
                    ),
                )
            }

            BookSource.INTERNET_ARCHIVE -> when (val r = archive.search(query.search, query.languages, 1)) {
                is ApiResult.Failure -> r
                is ApiResult.Success -> {
                    val body = r.value.response
                    ApiResult.Success(
                        CatalogPage(
                            results = body.docs.map { it.toItem() },
                            next = cursor(page = 2, fetched = body.docs.size, total = body.numFound),
                        ),
                    )
                }
            }

            BookSource.WIKISOURCE -> wikisourceFirst(query, BookSource.WIKISOURCE, wikisource)

            BookSource.WIKIBOOKS -> wikibooks?.let { wikisourceFirst(query, BookSource.WIKIBOOKS, it) }
                ?: ApiResult.Failure(ApiError.NotFound("Wikibooks not available"))
        }
    }

    /**
     * Halaman berikutnya. [cursor] opaque per sumber; [query] wajib sama
     * dengan saat halaman pertama diminta (ViewModel me-reset kursor tiap
     * kali filter berubah).
     */
    suspend fun nextPage(
        cursor: String,
        query: CatalogQuery,
        source: BookSource,
    ): ApiResult<CatalogPage> = withRetry {
        when (source) {
            // Cursor Gutenberg adalah URL API absolut yang dicek tepercaya.
            BookSource.GUTENDEX -> gutendex.fetchPage(cursor).mapPage()

            BookSource.OPEN_LIBRARY -> {
                val page = cursor.removePrefix("page=").toIntOrNull() ?: 1
                when (val r = openLibrary.search(query.search, query.languages, page)) {
                    is ApiResult.Failure -> r
                    is ApiResult.Success -> ApiResult.Success(
                        CatalogPage(
                            results = r.value.docs.map { it.toItem() },
                            next = cursor(page + 1, r.value.docs.size, r.value.numFound),
                        ),
                    )
                }
            }

            BookSource.INTERNET_ARCHIVE -> {
                val page = cursor.removePrefix("page=").toIntOrNull() ?: 1
                when (val r = archive.search(query.search, query.languages, page)) {
                    is ApiResult.Failure -> r
                    is ApiResult.Success -> {
                        val body = r.value.response
                        ApiResult.Success(
                            CatalogPage(
                                results = body.docs.map { it.toItem() },
                                next = cursor(page + 1, body.docs.size, body.numFound),
                            ),
                        )
                    }
                }
            }

            BookSource.WIKISOURCE -> wikisourceNext(cursor, query, BookSource.WIKISOURCE, wikisource)

            BookSource.WIKIBOOKS -> wikibooks?.let { wikisourceNext(cursor, query, BookSource.WIKIBOOKS, it) }
                ?: ApiResult.Failure(ApiError.NotFound("Wikibooks not available"))
        }
    }

    // ------------------------------------------------------------------
    // Detail per sumber
    // ------------------------------------------------------------------

    /**
     * Ambil detail satu buku berdasarkan kunci komposit.
     *
     *  * `gutenberg:` -> `GET /books/?ids=…` (format langsung tersedia);
     *  * `openlibrary:` -> karya + edisi -> `ocaid` -> metadata IA
     *    (unduhan memakai item Internet Archive milik karya itu);
     *  * `archive:` -> `metadata/{identifier}` -> pilih file per MIME;
     *  * `wikisource:` -> `action=parse` -> ringkasan teks + payload HTML.
     */
    suspend fun fetchDetail(key: String): ApiResult<CatalogDetail> = withRetry {
        when (BookSource.fromKey(key)) {
            BookSource.GUTENDEX -> gutenbergDetail(key)
            BookSource.OPEN_LIBRARY -> openLibraryDetail(key)
            BookSource.INTERNET_ARCHIVE -> archiveDetail(key)
            BookSource.WIKISOURCE -> wikisourceDetail(key, BookSource.WIKISOURCE, wikisource)
            BookSource.WIKIBOOKS -> wikibooks?.let { wikisourceDetail(key, BookSource.WIKIBOOKS, it) }
                ?: ApiResult.Failure(ApiError.NotFound("Wikibooks not available"))
        }
    }

    private suspend fun gutenbergDetail(key: String): ApiResult<CatalogDetail> {
        val id = BookSource.GUTENDEX.remoteIdOf(key)?.toIntOrNull()
            ?: return ApiResult.Failure(ApiError.NotFound("Unrecognised book key: $key"))
        return when (val r = gutendex.fetchPage(CatalogQuery(ids = id))) {
            is ApiResult.Failure -> r
            is ApiResult.Success -> {
                val found = r.value.results.firstOrNull { it.key == key }
                    ?: r.value.results.firstOrNull()
                    ?: return ApiResult.Failure(ApiError.NotFound("Book $key was not found"))
                ApiResult.Success(CatalogDetail(CatalogItem.fromGutendex(found)))
            }
        }
    }

    private suspend fun openLibraryDetail(key: String): ApiResult<CatalogDetail> {
        val workId = BookSource.OPEN_LIBRARY.remoteIdOf(key)
            ?: return ApiResult.Failure(ApiError.NotFound("Unrecognised book key: $key"))

        val work = when (val r = openLibrary.work(workId)) {
            is ApiResult.Failure -> return r
            is ApiResult.Success -> r.value
        }
        val editions = when (val r = openLibrary.editions(workId)) {
            is ApiResult.Failure -> return r
            is ApiResult.Success -> r.value
        }

        // Unduhan ditemukan lewat ocaid -> metadata Internet Archive.
        var formats: Map<String, String> = emptyMap()
        for (entry in editions.entries) {
            val ocaid = entry.ocaid?.takeIf { it.isNotBlank() } ?: continue
            val meta = archive.metadata(ocaid)
            if (meta is ApiResult.Success) {
                formats = ArchiveFormats.pick(meta.value.files, ocaid)
                if (formats.isNotEmpty()) break
            }
        }

        val authorNames = work.authors.take(2).mapNotNull { ref ->
            val authorId = ref.key.substringAfterLast('/')
            when (val r = openLibrary.author(authorId)) {
                is ApiResult.Success -> r.value.name?.takeIf { it.isNotBlank() }
                is ApiResult.Failure -> null
            }
        }

        val coverId = work.covers.firstOrNull()
        val item = CatalogItem(
            key = key,
            title = work.title ?: workId,
            authors = authorNames,
            summary = work.descriptionText?.take(600),
            subjects = emptyList(),
            languages = emptyList(),
            coverUrl = coverId?.let { "https://covers.openlibrary.org/b/id/$it-M.jpg" },
            downloadCount = null,
            copyright = null,
            source = BookSource.OPEN_LIBRARY,
            formats = formats,
            canonicalUrl = "https://openlibrary.org/works/$workId",
        )
        return ApiResult.Success(CatalogDetail(item))
    }

    private suspend fun archiveDetail(key: String): ApiResult<CatalogDetail> {
        val identifier = BookSource.INTERNET_ARCHIVE.remoteIdOf(key)
            ?: return ApiResult.Failure(ApiError.NotFound("Unrecognised book key: $key"))
        return when (val r = archive.metadata(identifier)) {
            is ApiResult.Failure -> r
            is ApiResult.Success -> {
                val info = r.value.metadata
                val item = CatalogItem(
                    key = key,
                    title = info?.title.elementText() ?: identifier,
                    authors = info?.creator.elementTextList(),
                    summary = info?.description.elementText()?.take(600),
                    subjects = info?.subject.elementTextList(),
                    languages = info?.language.elementTextList(),
                    coverUrl = "https://archive.org/services/img/$identifier",
                    downloadCount = null,
                    copyright = null,
                    source = BookSource.INTERNET_ARCHIVE,
                    formats = ArchiveFormats.pick(r.value.files, identifier),
                    canonicalUrl = "https://archive.org/details/$identifier",
                )
                ApiResult.Success(CatalogDetail(item))
            }
        }
    }

    private suspend fun wikisourceDetail(
        key: String,
        source: BookSource,
        api: WikisourceApi?,
    ): ApiResult<CatalogDetail> {
        if (api == null) return ApiResult.Failure(ApiError.NotFound("Source not available"))
        val pageid = source.remoteIdOf(key)?.toLongOrNull()
            ?: return ApiResult.Failure(ApiError.NotFound("Unrecognised book key: $key"))
        return when (val r = api.parse(pageid)) {
            is ApiResult.Failure -> r
            is ApiResult.Success -> {
                val summary = HtmlStrip.toText(r.value.text).take(400)
                val wrapped = WikisourceApi.wrapDocument(r.value.title, r.value.text)
                val baseUrl = if (source == BookSource.WIKIBOOKS) WikisourceApi.WIKIBOOKS_BASE_URL
                    else WikisourceApi.DEFAULT_BASE_URL
                ApiResult.Success(CatalogDetail(r.value.toItem(pageid, summary, baseUrl, source), payload = wrapped))
            }
        }
    }

    // ------------------------------------------------------------------
    // Wikisource: pencarian vs browse + kursor
    // ------------------------------------------------------------------

    private suspend fun wikisourceFirst(
        query: CatalogQuery,
        source: BookSource,
        api: WikisourceApi?,
    ): ApiResult<CatalogPage> {
        if (api == null) return ApiResult.Failure(ApiError.NotFound("Source not available"))
        val term = query.search?.trim()
        return if (term.isNullOrEmpty()) {
            when (val r = api.browse(null)) {
                is ApiResult.Failure -> r
                is ApiResult.Success -> ApiResult.Success(wikisourcePage(r.value, browsing = true, source))
            }
        } else {
            when (val r = api.search(term, 0)) {
                is ApiResult.Failure -> r
                is ApiResult.Success -> ApiResult.Success(wikisourcePage(r.value, browsing = false, source))
            }
        }
    }

    private suspend fun wikisourceNext(
        cursor: String,
        query: CatalogQuery,
        source: BookSource,
        api: WikisourceApi?,
    ): ApiResult<CatalogPage> {
        if (api == null) return ApiResult.Failure(ApiError.NotFound("Source not available"))
        return when {
            cursor.startsWith("browse:") -> when (val r = api.browse(cursor.removePrefix("browse:"))) {
                is ApiResult.Failure -> r
                is ApiResult.Success -> ApiResult.Success(wikisourcePage(r.value, browsing = true, source))
            }

            cursor.startsWith("search:") -> {
                val term = query.search?.trim().orEmpty()
                val offset = cursor.removePrefix("search:").toIntOrNull() ?: 0
                when (val r = api.search(term, offset)) {
                    is ApiResult.Failure -> r
                    is ApiResult.Success -> ApiResult.Success(wikisourcePage(r.value, browsing = false, source))
                }
            }

            else -> ApiResult.Failure(ApiError.InvalidUrl("Unknown cursor"))
        }
    }

    private fun wikisourcePage(response: WikisourceSearchResponse, browsing: Boolean, source: BookSource): CatalogPage {
        val data = response.query ?: return CatalogPage(emptyList())
        val hits = if (browsing) data.allpages else data.search
        val baseUrl = if (source == BookSource.WIKIBOOKS) WikisourceApi.WIKIBOOKS_BASE_URL
            else WikisourceApi.DEFAULT_BASE_URL
        val results = hits.map { hit ->
            val pageUrl = "$baseUrl/wiki/" + hit.title.replace(" ", "_")
            CatalogItem(
                key = source.prefix + hit.pageid,
                title = hit.title,
                authors = emptyList(),
                summary = null,
                subjects = emptyList(),
                languages = listOf("id"),
                coverUrl = null,
                downloadCount = null,
                copyright = null,
                source = source,
                formats = mapOf(Formats.MIME_HTML to pageUrl),
                canonicalUrl = pageUrl,
            )
        }
        val cont = response.cont
        val next = when {
            browsing && !cont?.apcontinue.isNullOrBlank() -> "browse:${cont?.apcontinue}"
            !browsing && (cont?.sroffset ?: 0) > 0 -> "search:${cont?.sroffset}"
            else -> null
        }
        return CatalogPage(results, next)
    }

    // ------------------------------------------------------------------
    // Utilitas
    // ------------------------------------------------------------------

    private fun cursor(page: Int, fetched: Int, total: Int): String? =
        if (fetched >= OpenLibraryApi.PAGE_SIZE && (page - 1) * OpenLibraryApi.PAGE_SIZE < total) {
            "page=$page"
        } else {
            null
        }

    private fun ApiResult<com.luread.app.data.network.GutendexPage>.mapPage(): ApiResult<CatalogPage> =
        when (this) {
            is ApiResult.Failure -> this
            is ApiResult.Success -> ApiResult.Success(
                CatalogPage(
                    results = value.results.map { CatalogItem.fromGutendex(it) },
                    next = value.next?.takeIf { GutendexApi.isTrustedCatalogUrl(it) },
                ),
            )
        }

    private suspend fun <T> withRetry(block: suspend () -> ApiResult<T>): ApiResult<T> {
        var attempt = 0
        var delayMs = baseDelayMs
        while (true) {
            val result = runCatching { block() }
                .getOrElse { t ->
                    if (t is CancellationException) throw t
                    ApiResult.Failure(ApiError.Network(t.message ?: "Network error"))
                }

            when {
                result is ApiResult.Success -> return result
                result is ApiResult.Failure && shouldRetry(result.error, attempt) -> {
                    attempt += 1
                    delay(delayMs)
                    delayMs *= 2
                }
                else -> return result
            }
        }
    }

    private fun shouldRetry(error: ApiError, attempt: Int): Boolean = when {
        attempt >= maxRetries -> false
        error is ApiError.Network || error is ApiError.Timeout -> true
        error is ApiError.Http && error.code >= 500 -> attempt < 1
        else -> false
    }
}
