package com.luread.app.data.manga

import com.luread.app.data.network.ApiError
import com.luread.app.data.network.ApiResult
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Klien MangaDex (tanpa API key).
 *
 * Diverifikasi dari dokumentasi resmi 2026-10-13 (https://api.mangadex.org/docs):
 *  * `GET /manga` — cari/browse manga (limit max 100, offset pagination);
 *  * `GET /manga/{id}/feed` — daftar chapter (filter translatedLanguage);
 *  * `GET /at-home/server/{chapterId}` — baseUrl + hash + data[] untuk URL gambar.
 *
 * Aturan attribution (wajib): kredit MangaDex + kredit scanlation group.
 * Tidak ada iklan/layanan berbayar — sesuai syarat penggunaan API.
 *
 * Rate limit: jaga jeda minimal antar request agar tidak diblokir.
 */
class MangaDexApi(
    private val client: HttpClient,
    private val json: Json = defaultJson,
) {
    companion object {
        const val BASE_URL = "https://api.mangadex.org"
        const val USER_AGENT = "Luread/0.1.0 (Android manga reader; credit MangaDex)"
        const val PAGE_SIZE = 20
        const val MIN_INTERVAL_MS = 400L

        val defaultJson: Json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }

        /** URL cover manga (256px). */
        fun coverUrl(mangaId: String, fileName: String): String =
            "https://uploads.mangadex.org/covers/$mangaId/$fileName.256.jpg"

        /** URL pencarian/browse manga. [order] salah satu: followedCount/latestUploadedChapter/relevance. */
        fun mangaListUrl(
            title: String? = null,
            limit: Int = PAGE_SIZE,
            offset: Int = 0,
            order: String = "latestUploadedChapter",
            contentRating: String = "safe",
        ): String {
            val builder = URLBuilder("$BASE_URL/manga")
            builder.parameters.append("limit", limit.coerceIn(1, 100).toString())
            builder.parameters.append("offset", offset.coerceAtLeast(0).toString())
            builder.parameters.append("contentRating[]", contentRating)
            builder.parameters.append("includes[]", "cover_art")
            builder.parameters.append("includes[]", "author")
            if (!title.isNullOrBlank()) {
                builder.parameters.append("title", title.trim())
                builder.parameters.append("order[relevance]", "desc")
            } else {
                builder.parameters.append("order[$order]", "desc")
            }
            return builder.buildString()
        }

        /**
         * URL feed chapter satu manga.
         * [languages] daftar kode translatedLanguage; kosong = semua bahasa.
         */
        fun feedUrl(
            mangaId: String,
            limit: Int = 100,
            offset: Int = 0,
            languages: List<String> = listOf("id", "en"),
        ): String {
            val builder = URLBuilder("$BASE_URL/manga/$mangaId/feed")
            builder.parameters.append("limit", limit.coerceIn(1, 500).toString())
            builder.parameters.append("offset", offset.coerceAtLeast(0).toString())
            languages.forEach { lang ->
                builder.parameters.append("translatedLanguage[]", lang)
            }
            builder.parameters.append("order[chapter]", "asc")
            return builder.buildString()
        }

        /** URL detail satu manga (includes cover_art + author). */
        fun mangaByIdUrl(mangaId: String): String {
            val builder = URLBuilder("$BASE_URL/manga/$mangaId")
            builder.parameters.append("includes[]", "cover_art")
            builder.parameters.append("includes[]", "author")
            return builder.buildString()
        }

        /** URL at-home server untuk satu chapter. */
        fun atHomeUrl(chapterId: String): String = "$BASE_URL/at-home/server/$chapterId"
    }

    @Volatile
    private var lastRequestAt = 0L

    private suspend fun <T> getJson(url: String, decode: (String) -> T): ApiResult<T> {
        // Jaga jeda minimal agar tidak kena rate limit.
        val wait = MIN_INTERVAL_MS - (System.currentTimeMillis() - lastRequestAt)
        if (wait > 0) kotlinx.coroutines.delay(wait)
        lastRequestAt = System.currentTimeMillis()

        val response = try {
            client.get(url) {
                header(HttpHeaders.UserAgent, USER_AGENT)
                accept(ContentType.Application.Json)
                // LUREAD-FEAT: timeout dari Pengaturan diterapkan per-request.
                com.luread.app.data.network.NetworkTimeout.applyTo(this)
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            val message = t.message ?: t::class.simpleName ?: "Network error"
            return when (t) {
                is SocketTimeoutException -> ApiResult.Failure(ApiError.Timeout(message))
                is IOException -> ApiResult.Failure(ApiError.Network(message))
                else -> ApiResult.Failure(ApiError.Network(message))
            }
        }

        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            return ApiResult.Failure(ApiError.Http(response.status.value, body.take(160)))
        }
        return try {
            ApiResult.Success(decode(body))
        } catch (e: Exception) {
            ApiResult.Failure(ApiError.NotJson("Malformed JSON: ${e.message}"))
        }
    }

    suspend fun searchManga(title: String?, limit: Int = PAGE_SIZE, offset: Int = 0): ApiResult<MangaDexListResponse> =
        getJson(mangaListUrl(title = title, limit = limit, offset = offset)) {
            json.decodeFromString(MangaDexListResponse.serializer(), it)
        }

    suspend fun popularManga(limit: Int = PAGE_SIZE, offset: Int = 0): ApiResult<MangaDexListResponse> =
        getJson(mangaListUrl(order = "followedCount", limit = limit, offset = offset)) {
            json.decodeFromString(MangaDexListResponse.serializer(), it)
        }

    suspend fun latestManga(limit: Int = PAGE_SIZE, offset: Int = 0): ApiResult<MangaDexListResponse> =
        getJson(mangaListUrl(order = "latestUploadedChapter", limit = limit, offset = offset)) {
            json.decodeFromString(MangaDexListResponse.serializer(), it)
        }

    suspend fun mangaById(mangaId: String): ApiResult<MangaDexManga> =
        getJson(mangaByIdUrl(mangaId)) {
            json.decodeFromString(MangaDexMangaResponse.serializer(), it).data
                ?: throw IllegalStateException("Empty manga payload")
        }

    suspend fun chapters(
        mangaId: String,
        limit: Int = 100,
        offset: Int = 0,
        languages: List<String> = listOf("id", "en"),
    ): ApiResult<MangaDexFeedResponse> =
        getJson(feedUrl(mangaId, limit, offset, languages)) {
            json.decodeFromString(MangaDexFeedResponse.serializer(), it)
        }

    suspend fun atHome(chapterId: String): ApiResult<MangaDexAtHome> =
        getJson(atHomeUrl(chapterId)) {
            json.decodeFromString(MangaDexAtHome.serializer(), it)
        }
}
