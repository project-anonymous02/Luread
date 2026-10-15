package com.luread.app.data.komiku

import com.luread.app.data.network.ApiError
import com.luread.app.data.network.ApiResult
import com.luread.app.data.network.NetworkTimeout
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLEncoder

/**
 * Klien sumber manga Komiku (LUREAD-FEAT) — teknik scraping dipelajari dari
 * `github.com/febryardiansyah/manga-api` (Express + Cheerio atas Komiku),
 * diadaptasi ke Ktor + Jsoup dengan selector diverifikasi 2026-10-15:
 *  * cari   : `api.komiku.org/?post_type=manga&s=…` (fragment kartu `.bge`)
 *  * detail : `komiku.org/manga/<slug>/` (tabel `#Daftar_Chapter`)
 *  * gambar : `komiku.org/<chapter>/` (`#Baca_Komik img.klazy`)
 *
 * Id sumber ini memakai prefiks [PREFIX] agar bisa diganggu-ruang dengan id
 * MangaDex di rute navigasi yang sama (id selalu tanpa slash).
 *
 * Atribusi: konten milik Komiku; format scraping mengikuti proyek
 * febryardiansyah/manga-api.
 */
class KomikuApi(private val client: HttpClient) {

    companion object {
        /** Prefiks id manga/chapter Komiku di seluruh aplikasi. */
        const val PREFIX = "komiku:"
        const val SEARCH_URL = "https://api.komiku.org/"
        const val SITE_URL = "https://komiku.org"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        const val MIN_INTERVAL_MS = 400L

        fun detailUrl(slug: String): String = "$SITE_URL/manga/$slug/"
        fun chapterUrl(path: String): String = "$SITE_URL/$path/"

        fun searchUrl(query: String): String =
            "$SEARCH_URL?post_type=manga&s=${URLEncoder.encode(query.trim(), "UTF-8")}"

        /**
         * Kandidat kata kunci pencarian. Komiku butuh kata berspasi —
         * "SPY×FAMILY" tanpa spasi menghasilkan 0 hasil, jadi varian kedua
         * memecah tanda ×/simbol menjadi spasi ("SPY FAMILY" → cocok).
         */
        fun searchQueries(title: String): List<String> = listOfNotNull(
            title.trim().ifBlank { null },
            title.replace(Regex("""[×✕*]"""), " ")
                .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
                .trim()
                .ifBlank { null },
        ).distinct()

        /** `"komiku:slug"` → `"slug"`; bukan id Komiku → null. */
        fun slugOrNull(id: String): String? =
            id.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.ifBlank { null }

        /** `"komiku:chapter-path"` → `"chapter-path"`; bukan id Komiku → null. */
        fun chapterOrNull(id: String): String? =
            id.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.ifBlank { null }
    }

    @Volatile
    private var lastRequestAt = 0L

    private suspend fun fetchHtml(url: String): ApiResult<String> {
        // Jaga jeda minimal agar tidak diburu-buru (pola MangaDexApi).
        val wait = MIN_INTERVAL_MS - (System.currentTimeMillis() - lastRequestAt)
        if (wait > 0) kotlinx.coroutines.delay(wait)
        lastRequestAt = System.currentTimeMillis()

        val response = try {
            client.get(url) {
                header(HttpHeaders.UserAgent, USER_AGENT)
                header("Referer", "$SITE_URL/")
                accept(ContentType.Text.Html)
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
            return ApiResult.Failure(ApiError.Http(response.status.value, body.take(160)))
        }
        return ApiResult.Success(body)
    }

    /** Cari manga di Komiku. Coba beberapa bentuk kata kunci; kandidat pertama
     *  yang menghasilkan kartu menang. Gagal = bukan bencana (sumber sekunder). */
    suspend fun search(query: String): ApiResult<List<KomikuParser.SearchItem>> {
        for (q in searchQueries(query)) {
            when (val r = fetchHtml(searchUrl(q))) {
                is ApiResult.Failure -> return r
                is ApiResult.Success -> {
                    val items = KomikuParser.parseSearch(r.value)
                    if (items.isNotEmpty()) return ApiResult.Success(items)
                }
            }
        }
        return ApiResult.Success(emptyList())
    }

    /** Detail manga + daftar chapter lengkap (satu halaman, tanpa paginasi). */
    suspend fun detail(slug: String): ApiResult<KomikuParser.Detail> =
        when (val r = fetchHtml(detailUrl(slug))) {
            is ApiResult.Failure -> r
            is ApiResult.Success -> ApiResult.Success(KomikuParser.parseDetail(r.value, slug))
        }

    /** URL gambar halaman chapter (hanya gambar konten, iklan tersaring). */
    suspend fun pages(chapterPath: String): ApiResult<List<String>> =
        when (val r = fetchHtml(chapterUrl(chapterPath))) {
            is ApiResult.Failure -> r
            is ApiResult.Success -> {
                val images = KomikuParser.parseChapterImages(r.value)
                if (images.isEmpty()) {
                    ApiResult.Failure(ApiError.NotFound("No pages in this chapter"))
                } else {
                    ApiResult.Success(images)
                }
            }
        }
}
