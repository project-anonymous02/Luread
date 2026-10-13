package com.luread.app.data.network

import com.luread.app.data.catalog.CatalogLanguages
import io.ktor.client.HttpClient
import io.ktor.http.URLBuilder
import kotlinx.serialization.json.Json

/**
 * Klien Internet Archive Advanced Search (tanpa API key).
 *
 * Diverifikasi langsung 2026-10-13 (docs/book-apis-audit.md):
 *  * `advancedsearch.php?…&output=json` 200; `sort[]=downloads desc` valid;
 *  * `language:ind` menyaring (20.456 hasil teks Indonesia);
 *  * `subject:fiction AND format:EPUB` memberi browse buku populer yang wajar
 *    ketika pencarian kosong (pilihan `downloads desc` murni penuh sampah);
 *  * `title:(…)` memakai frasa; tanda kutip dan backslash di-escape;
 *  * `metadata/{identifier}` memuat `metadata` + `files` (untuk detail).
 */
class ArchiveApi(
    private val client: HttpClient,
    private val json: Json = GutendexApi.defaultJson,
) {
    companion object {
        const val BASE_URL = "https://archive.org"
        const val PAGE_SIZE = 20

        val SEARCH_FIELDS = listOf(
            "identifier", "title", "creator", "description",
            "language", "downloads", "subject", "year",
        )

        /** Filter dasar: teks + format yang bisa dibaca Luread. */
        const val BASE_QUERY = "mediatype:texts AND format:(EPUB OR \"Text PDF\" OR DjVuTXT)"

        /** Browse ketika pencarian kosong (diverifikasi memberi hasil buku). */
        const val DEFAULT_TAIL = " AND subject:fiction"

        fun searchUrl(
            search: String?,
            languages: List<String>,
            page: Int,
            topic: String? = null,
        ): String {
            val text = search?.trim()
            val query = buildString {
                append(BASE_QUERY)
                CatalogLanguages.isoFor(languages)?.let { append(" AND language:").append(it) }
                topic?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    append(" AND subject:").append(escapePhrase(it))
                }
                if (text.isNullOrEmpty()) {
                    if (topic.isNullOrBlank()) append(DEFAULT_TAIL)
                } else {
                    append(" AND title:(").append(escapePhrase(text)).append(")")
                }
            }
            val builder = URLBuilder("$BASE_URL/advancedsearch.php")
            builder.parameters.append("q", query)
            builder.parameters.append("rows", PAGE_SIZE.toString())
            builder.parameters.append("page", page.coerceAtLeast(1).toString())
            builder.parameters.append("output", "json")
            SEARCH_FIELDS.forEach { builder.parameters.append("fl[]", it) }
            if (text.isNullOrEmpty() && topic.isNullOrBlank()) {
                builder.parameters.append("sort[]", "downloads desc")
            }
            return builder.buildString()
        }

        fun metadataUrl(identifier: String): String = "$BASE_URL/metadata/$identifier"

        /** Bungkus frasa pencarian agar karakter khusus SOLR tidak merusak query. */
        fun escapePhrase(text: String): String =
            "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }

    suspend fun search(
        search: String?,
        languages: List<String>,
        page: Int,
        topic: String? = null,
    ): ApiResult<ArchiveSearchResponse> =
        fetchJson(client, searchUrl(search, languages, page, topic)) {
            json.decodeFromString(ArchiveSearchResponse.serializer(), it)
        }

    suspend fun metadata(identifier: String): ApiResult<ArchiveMetadata> =
        fetchJson(client, metadataUrl(identifier)) {
            json.decodeFromString(ArchiveMetadata.serializer(), it)
        }
}
