package com.luread.app.data.network

import com.luread.app.data.catalog.CatalogLanguages
import io.ktor.client.HttpClient
import io.ktor.http.URLBuilder
import kotlinx.serialization.json.Json

/**
 * Klien Open Library (tanpa API key).
 *
 * Perilaku yang dipastikan lewat uji langsung pada 2026-10-13
 * (docs/book-apis-audit.md):
 *  * `search.json` tanpa `q` membalas `numFound: 0` -> pencarian kosong
 *    memakai `q=subject:fiction` + `sort=editions` (browse populer);
 *  * `language=eng|ind|…` menyaring hasil sesuai ISO 639-2;
 *  * `page` 1-based; `limit=20` halaman penuh;
 *  * `works/{id}.json` dan `works/{id}/editions.json` membalas 200 JSON;
 *    `ocaid` pada edisi menunjuk item Internet Archive yang bisa diunduh.
 */
class OpenLibraryApi(
    private val client: HttpClient,
    private val json: Json = GutendexApi.defaultJson,
) {
    companion object {
        const val BASE_URL = "https://openlibrary.org"
        const val PAGE_SIZE = 20

        /** Browse bila pencarian kosong (diverifikasi: `subject:fiction` terpilih). */
        const val DEFAULT_QUERY = "subject:fiction"

        val SEARCH_FIELDS = listOf(
            "key", "title", "author_name", "cover_i", "ia",
            "language", "subject", "first_publish_year",
        )

        /** URL pencarian untuk satu halaman (page 1-based). */
        fun searchUrl(search: String?, languages: List<String>, page: Int): String {
            val builder = URLBuilder("$BASE_URL/search.json")
            builder.parameters.append("q", search?.trim().takeUnless { it.isNullOrEmpty() } ?: DEFAULT_QUERY)
            builder.parameters.append("sort", "editions")
            builder.parameters.append("limit", PAGE_SIZE.toString())
            builder.parameters.append("page", page.coerceAtLeast(1).toString())
            builder.parameters.append("fields", SEARCH_FIELDS.joinToString(","))
            CatalogLanguages.isoFor(languages)?.let {
                builder.parameters.append("language", it)
            }
            return builder.buildString()
        }

        /** URL metadata karya (deskripsi). */
        fun workUrl(workId: String): String = "$BASE_URL/works/$workId.json"

        /** URL daftar edisi (dipakai untuk menemukan `ocaid`). */
        fun editionsUrl(workId: String): String = "$BASE_URL/works/$workId/editions.json?limit=10"

        /** URL metadata penulis (untuk menampilkan nama pengarang). */
        fun authorUrl(authorId: String): String = "$BASE_URL/authors/$authorId.json"
    }

    suspend fun search(search: String?, languages: List<String>, page: Int): ApiResult<OpenLibrarySearchResponse> =
        fetchJson(client, searchUrl(search, languages, page)) {
            json.decodeFromString(OpenLibrarySearchResponse.serializer(), it)
        }

    suspend fun work(workId: String): ApiResult<OpenLibraryWork> =
        fetchJson(client, workUrl(workId)) {
            json.decodeFromString(OpenLibraryWork.serializer(), it)
        }

    suspend fun editions(workId: String): ApiResult<OpenLibraryEditionsResponse> =
        fetchJson(client, editionsUrl(workId)) {
            json.decodeFromString(OpenLibraryEditionsResponse.serializer(), it)
        }

    suspend fun author(authorId: String): ApiResult<OpenLibraryAuthor> =
        fetchJson(client, authorUrl(authorId)) {
            json.decodeFromString(OpenLibraryAuthor.serializer(), it)
        }
}
