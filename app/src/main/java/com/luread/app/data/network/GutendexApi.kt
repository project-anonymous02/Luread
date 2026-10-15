package com.luread.app.data.network

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
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.SocketTimeoutException

/** Hasil request katalog yang selalu bisa dibedakan antara sukses dan gagal. */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

sealed class ApiError(open val message: String) {
    data class Network(override val message: String) : ApiError(message)
    data class Timeout(override val message: String = "Request timed out") : ApiError(message)
    data class Http(val code: Int, override val message: String) : ApiError(message)
    data class NotJson(override val message: String) : ApiError(message)
    data class InvalidUrl(override val message: String) : ApiError(message)
    data class NotFound(override val message: String) : ApiError(message)
}

/**
 * Klien REST Gutendex.
 *
 * Perilaku yang dipastikan lewat pengujian langsung (docs/gutendex-api-audit.md):
 *  * `GET /books` tanpa slash membalas 301 ke `/books/` -> gunakan slash;
 *  * `page` di luar jangkauan membalas 503 berupa HTML, bukan JSON;
 *  * `ids` multi-nilai teramati gagal konsisten -> tidak dipakai aplikasi;
 *  * satu halaman berisi 32 hasil; `next`/`previous` adalah URL absolut.
 */
class GutendexApi(
    private val client: HttpClient,
    private val json: Json = defaultJson,
) {
    companion object {
        const val BASE_URL = "https://gutendex.com"
        const val BOOKS_PATH = "$BASE_URL/books/"
        const val USER_AGENT = "Luread/0.1.0 (Android ebook reader)"
        const val PAGE_SIZE = 32

        val defaultJson: Json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }

        /** Hanya URL katalog yang dipercaya yang boleh diikuti (perlindungan SSRF). */
        fun isTrustedCatalogUrl(url: String): Boolean =
            url.startsWith("https://gutendex.com/books/", ignoreCase = true)
    }

    suspend fun fetchPage(query: CatalogQuery): ApiResult<GutendexPage> = execute(buildUrl(query))

    /** Mengikuti `next`/`previous` yang diberikan API. */
    suspend fun fetchPage(url: String): ApiResult<GutendexPage> {
        if (!isTrustedCatalogUrl(url)) {
            return ApiResult.Failure(ApiError.InvalidUrl("Refused untrusted catalog URL"))
        }
        return execute(url)
    }

    fun buildUrl(query: CatalogQuery): String {
        val builder = URLBuilder(BOOKS_PATH)
        query.search?.trim()?.takeIf { it.isNotEmpty() }?.let { builder.parameters.append("search", it) }
        query.languages.forEach { lang -> builder.parameters.append("languages", lang) }
        query.topic?.trim()?.takeIf { it.isNotEmpty() }?.let { builder.parameters.append("topic", it) }
        query.mimeType?.trim()?.takeIf { it.isNotEmpty() }?.let { builder.parameters.append("mime_type", it) }
        query.ids?.let { builder.parameters.append("ids", it.toString()) }
        query.page?.takeIf { it > 1 }?.let { builder.parameters.append("page", it.toString()) }
        return builder.buildString()
    }

    private suspend fun execute(url: String): ApiResult<GutendexPage> {
        val response = try {
            client.get(url) {
                header(HttpHeaders.UserAgent, USER_AGENT)
                accept(ContentType.Application.Json)
                // LUREAD-FEAT: timeout dari Pengaturan diterapkan per-request.
                NetworkTimeout.applyTo(this)
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
            return ApiResult.Failure(ApiError.Http(response.status.value, snippet(body)))
        }

        val contentType = response.headers[HttpHeaders.ContentType]
        if (contentType != null && !contentType.contains("json", ignoreCase = true)) {
            return ApiResult.Failure(ApiError.NotJson("Unexpected content type: $contentType"))
        }

        return try {
            ApiResult.Success(json.decodeFromString(GutendexPage.serializer(), body))
        } catch (e: SerializationException) {
            ApiResult.Failure(ApiError.NotJson("Malformed JSON: ${e.message}"))
        } catch (e: IllegalArgumentException) {
            ApiResult.Failure(ApiError.NotJson("Unreadable JSON: ${e.message}"))
        }
    }

    private fun snippet(body: String): String =
        body.replace(Regex("\\s+"), " ").trim().take(160)
}
