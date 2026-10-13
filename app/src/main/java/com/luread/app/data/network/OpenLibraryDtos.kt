package com.luread.app.data.network

import com.luread.app.data.catalog.BookSource
import com.luread.app.data.catalog.CatalogItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Model JSON Open Library berdasarkan uji langsung 2026-10-13
 * (lihat docs/book-apis-audit.md):
 *  * `search.json` tanpa `q` membalas `numFound: 0` -> pencarian kosong
 *    memakai `q=subject:fiction&sort=editions` (browse populer);
 *  * `language=ind` menyaring hasil (520 vs 621 pada `cerita rakyat`);
 *  * `cover_i`/`ia` bersifat opsional -> semua field diberi default aman;
 *  * deskripsi karya berupa string ATAU `{type, value}`.
 */
@Serializable
data class OpenLibrarySearchResponse(
    val numFound: Int = 0,
    val start: Int = 0,
    val docs: List<OpenLibraryDoc> = emptyList(),
)

@Serializable
data class OpenLibraryDoc(
    val key: String = "",
    val title: String = "",
    @SerialName("author_name") val authorNames: List<String> = emptyList(),
    @SerialName("cover_i") val coverId: Int? = null,
    @SerialName("first_publish_year") val firstPublishYear: Int? = null,
    val ia: List<String> = emptyList(),
    val language: List<String> = emptyList(),
    val subject: List<String> = emptyList(),
) {
    /** ID karya: `/works/OL66554W` -> `OL66554W` (kunci tanpa slash). */
    val workId: String
        get() = key.substringAfterLast('/')

    val canonicalUrl: String
        get() = "https://openlibrary.org/works/$workId"

    fun toItem(): CatalogItem = CatalogItem(
        key = BookSource.OPEN_LIBRARY.prefix + workId,
        title = title,
        authors = authorNames,
        summary = null,
        subjects = subject,
        languages = language,
        coverUrl = coverId?.let { "https://covers.openlibrary.org/b/id/$it-M.jpg" },
        downloadCount = null,
        copyright = null,
        source = BookSource.OPEN_LIBRARY,
        canonicalUrl = canonicalUrl,
    )
}

@Serializable
data class OpenLibraryWork(
    val title: String? = null,
    val description: JsonElement? = null,
    val covers: List<Int> = emptyList(),
    val authors: List<OpenLibraryAuthorRef> = emptyList(),
) {
    /** Deskripsi string atau objek `{type, value}`; null bila tidak ada. */
    val descriptionText: String?
        get() {
            val raw = when (val d = description) {
                is JsonNull -> null
                is JsonPrimitive -> d.content
                is JsonObject -> (d["value"] as? JsonPrimitive)?.let {
                    if (it is JsonNull) null else it.content
                }
                else -> null
            }
            return raw?.trim()?.takeIf { it.isNotEmpty() }
        }
}

@Serializable
data class OpenLibraryAuthorRef(
    val author: OpenLibraryAuthorKey? = null,
) {
    val key: String
        get() = author?.key.orEmpty()
}

@Serializable
data class OpenLibraryAuthorKey(
    val key: String = "",
)

@Serializable
data class OpenLibraryAuthor(
    val name: String? = null,
)

@Serializable
data class OpenLibraryEditionsResponse(
    val entries: List<OpenLibraryEdition> = emptyList(),
)

@Serializable
data class OpenLibraryEdition(
    val key: String = "",
    val ocaid: String? = null,
)
