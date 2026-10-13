package com.luread.app.data.network

import com.luread.app.data.catalog.BookSource
import com.luread.app.data.catalog.CatalogItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Model JSON MediaWiki Wikisource Indonesia (uji langsung 2026-10-13,
 * docs/book-apis-audit.md):
 *  * `action=query&list=search&formatversion=2` membalas `query.search[]`
 *    berisi `pageid` + `title`, dilanjutkan lewat `continue.sroffset`;
 *  * `list=allpages` (browse tanpa kata kunci) memakai `continue.apcontinue`
 *    berupa judul halaman terakhir;
 *  * `prop=extracts` DIABAIKAN karena wiki ini membalas extract kosong;
 *    gunakan `action=parse&prop=text` (HTML) — diverifikasi 52 KB nyata;
 *  * semua konten berbahasa Indonesia karena hanya wiki `id.wikisource.org`.
 */
@Serializable
data class WikisourceSearchResponse(
    val query: WikisourceQuery? = null,
    @SerialName("continue") val cont: WikisourceContinue? = null,
)

@Serializable
data class WikisourceQuery(
    val search: List<WikisourceHit> = emptyList(),
    val allpages: List<WikisourceHit> = emptyList(),
)

@Serializable
data class WikisourceHit(
    val pageid: Long = 0,
    val title: String = "",
)

@Serializable
data class WikisourceContinue(
    val sroffset: Int? = null,
    val apcontinue: String? = null,
)

@Serializable
data class WikisourceParseResponse(
    val parse: WikisourceParse? = null,
)

@Serializable
data class WikisourceParse(
    val title: String = "",
    val text: String = "",
) {
    val canonicalUrl: String
        get() = "https://id.wikisource.org/wiki/" +
            title.replace(" ", "_").split("/").joinToString("/") { segment ->
                java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
            }

    fun toItem(pageid: Long, summary: String?): CatalogItem = CatalogItem(
        key = BookSource.WIKISOURCE.prefix + pageid,
        title = title,
        authors = emptyList(),
        summary = summary,
        subjects = emptyList(),
        languages = listOf("id"),
        coverUrl = null,
        downloadCount = null,
        copyright = null,
        source = BookSource.WIKISOURCE,
        formats = mapOf(com.luread.app.data.catalog.Formats.MIME_HTML to canonicalUrl),
        canonicalUrl = canonicalUrl,
    )
}
