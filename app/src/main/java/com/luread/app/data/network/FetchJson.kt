package com.luread.app.data.network

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Ambil URL dan dekode JSON-nya dengan pemetaan galat yang sama seperti
 * GutendexApi (dipakai bersama oleh Open Library, Internet Archive, dan
 * Wikisource supaya lapisan katalog tidak perlu tahu perbedaannya).
 *
 * Kebijakan status:
 *  * status tidak 2xx -> [ApiError.Http] (badan disingkat untuk pesan);
 *  * content-type bukan JSON -> [ApiError.NotJson] (mis. halaman galat HTML);
 *  * timeout/IO -> [ApiError.Timeout]/[ApiError.Network];
 *  * pembatalan selalu dilempar kembali.
 */
internal suspend fun <T> fetchJson(
    client: HttpClient,
    url: String,
    decode: (String) -> T,
): ApiResult<T> {
    val response = try {
        client.get(url) {
            header(HttpHeaders.UserAgent, GutendexApi.USER_AGENT)
            accept(ContentType.Application.Json)
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
        ApiResult.Success(decode(body))
    } catch (e: SerializationException) {
        ApiResult.Failure(ApiError.NotJson("Malformed JSON: ${e.message}"))
    } catch (e: IllegalArgumentException) {
        ApiResult.Failure(ApiError.NotJson("Unreadable JSON: ${e.message}"))
    }
}

private fun snippet(body: String): String =
    body.replace(Regex("\\s+"), " ").trim().take(160)
