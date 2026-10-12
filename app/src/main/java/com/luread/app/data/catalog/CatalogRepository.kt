package com.luread.app.data.catalog

import com.luread.app.data.network.ApiError
import com.luread.app.data.network.ApiResult
import com.luread.app.data.network.GutendexApi
import com.luread.app.data.network.GutendexPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Repositori katalog: membangun permintaan, mencoba ulang kegagalan yang masuk
 * akal untuk dicoba ulang, dan mengikuti tautan halaman dari API.
 *
 * Kebijakan percobaan ulang (berbasis audit nyata, bukan tebakan):
 *  * gagal jaringan / timeout -> 2 kali percobaan ulang dengan jeda 400ms, 900ms;
 *  * HTTP 503 (teramati saat `page` di luar jangkauan atau burst) -> 1 kali
 *    percobaan ulang, lalu dilaporkan sebagai [ApiError.Http];
 *  * respons non-JSON (halaman galat HTML) -> tidak diulang, langsung gagal;
 *  * pembatalan (misal ViewModel beralih query) -> selalu dilempar kembali.
 */
class CatalogRepository(
    private val api: GutendexApi,
    private val maxRetries: Int = 2,
    private val baseDelayMs: Long = 400L,
) {
    /** Halaman pertama untuk sebuah query. */
    suspend fun firstPage(query: CatalogQuery): ApiResult<GutendexPage> = withRetry {
        api.fetchPage(query)
    }

    /** Halaman berikutnya/sebelumnya mengikuti URL `next`/`previous` API. */
    suspend fun pageAt(url: String): ApiResult<GutendexPage> = withRetry {
        api.fetchPage(url)
    }

    /** Info untuk tombol pagination: URL absolut dari API, dipercaya hanya untuk katalog. */
    fun trustedNext(response: GutendexPage): String? =
        response.next?.takeIf { GutendexApi.isTrustedCatalogUrl(it) }

    fun trustedPrevious(response: GutendexPage): String? =
        response.previous?.takeIf { GutendexApi.isTrustedCatalogUrl(it) }

    private suspend fun withRetry(block: suspend () -> ApiResult<GutendexPage>): ApiResult<GutendexPage> {
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
