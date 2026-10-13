package com.luread.app.data.manga

import com.luread.app.data.network.ApiResult

/**
 * Repositori manga: memetakan DTO MangaDex ke model UI dan mengelola
 * kredibilitas attribution (wajib mencantumkan MangaDex).
 */
class MangaRepository(private val api: MangaDexApi) {

    /** Ringkasan manga untuk kartu. */
    data class MangaSummary(
        val id: String,
        val title: String,
        val coverUrl: String?,
        val authorNames: List<String>,
        val tags: List<String>,
        val status: String?,
        val description: String?,
    )

    /** Satu chapter untuk daftar. */
    data class ChapterSummary(
        val id: String,
        val number: String?,
        val title: String?,
        val language: String?,
        val pages: Int?,
    )

    /** Hasil halaman manga. */
    data class MangaPage(
        val results: List<MangaSummary>,
        val offset: Int,
        val total: Int,
    ) {
        val nextOffset: Int? get() = if (offset + results.size < total) offset + results.size else null
    }

    fun toSummary(m: MangaDexManga): MangaSummary = MangaSummary(
        id = m.id,
        title = m.title,
        coverUrl = m.coverUrl,
        authorNames = m.authorNames,
        tags = m.tags,
        status = m.status,
        description = m.description,
    )

    suspend fun search(title: String?, limit: Int = MangaDexApi.PAGE_SIZE, offset: Int = 0): ApiResult<MangaPage> =
        when (val r = api.searchManga(title, limit, offset)) {
            is ApiResult.Failure -> r
            is ApiResult.Success -> ApiResult.Success(
                MangaPage(
                    results = r.value.data.map { toSummary(it) },
                    offset = r.value.offset,
                    total = r.value.total,
                ),
            )
        }

    suspend fun popular(limit: Int = MangaDexApi.PAGE_SIZE, offset: Int = 0): ApiResult<MangaPage> =
        when (val r = api.popularManga(limit, offset)) {
            is ApiResult.Failure -> r
            is ApiResult.Success -> ApiResult.Success(
                MangaPage(
                    results = r.value.data.map { toSummary(it) },
                    offset = r.value.offset,
                    total = r.value.total,
                ),
            )
        }

    suspend fun latest(limit: Int = MangaDexApi.PAGE_SIZE, offset: Int = 0): ApiResult<MangaPage> =
        when (val r = api.latestManga(limit, offset)) {
            is ApiResult.Failure -> r
            is ApiResult.Success -> ApiResult.Success(
                MangaPage(
                    results = r.value.data.map { toSummary(it) },
                    offset = r.value.offset,
                    total = r.value.total,
                ),
            )
        }

    /** Detail manga + chapter (untuk layar detail). */
    suspend fun detail(mangaId: String): ApiResult<Pair<MangaSummary, List<ChapterSummary>>> {
        val meta = when (val m = api.mangaById(mangaId)) {
            is ApiResult.Failure -> return m
            is ApiResult.Success -> toSummary(m.value)
        }
        return when (val c = chapters(mangaId)) {
            is ApiResult.Failure -> c
            is ApiResult.Success -> ApiResult.Success(meta to c.value)
        }
    }

    suspend fun chapters(mangaId: String): ApiResult<List<ChapterSummary>> =
        when (val r = api.chapters(mangaId)) {
            is ApiResult.Failure -> r
            is ApiResult.Success -> ApiResult.Success(
                r.value.data.map { ch ->
                    ChapterSummary(
                        id = ch.id,
                        number = ch.attributes?.chapter,
                        title = ch.attributes?.title,
                        language = ch.attributes?.translatedLanguage,
                        pages = ch.attributes?.pages,
                    )
                },
            )
        }

    /** URL gambar halaman chapter (kualitas data-saver agar hemat kuota). */
    suspend fun pageUrls(chapterId: String): ApiResult<List<String>> =
        when (val r = api.atHome(chapterId)) {
            is ApiResult.Failure -> r
            is ApiResult.Success -> {
                val chapter = r.value.chapter
                val base = r.value.baseUrl
                if (chapter == null || base.isBlank()) {
                    ApiResult.Failure(com.luread.app.data.network.ApiError.NotFound("No image data"))
                } else {
                    val urls = chapter.dataSaver.mapIndexedNotNull { i, _ ->
                        chapter.pageUrl(base, i, saver = true)
                    }.ifEmpty {
                        chapter.data.mapIndexedNotNull { i, _ -> chapter.pageUrl(base, i, saver = false) }
                    }
                    ApiResult.Success(urls)
                }
            }
        }
}
