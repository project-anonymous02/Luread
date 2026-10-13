package com.luread.app.data.catalog

import com.luread.app.data.network.GutendexBook

/**
 * Item katalog seragam untuk semua sumber.
 *
 * Layar Discover/Detail hanya mengenal model ini; mapper per-sumber
 * (Gutendex, Open Library, Internet Archive) mengisinya di
 * lapisan jaringan.
 *
 * [formats] mengikuti konvensi Gutendex: map MIME -> URL unduhan. Gutenberg
 * mengisinya dari API; sumber lain mengisinya saat detail diminta
 * (lihat CatalogRepository.fetchDetail).
 */
data class CatalogItem(
    val key: String,
    val title: String,
    val authors: List<String>,
    val summary: String?,
    val subjects: List<String>,
    val languages: List<String>,
    val coverUrl: String?,
    val downloadCount: Int?,
    val copyright: Boolean?,
    val source: BookSource,
    val formats: Map<String, String?> = emptyMap(),
    /** URL halaman resmi sumber (bukan berkas unduhan). */
    val canonicalUrl: String? = null,
) {
    companion object {
        fun fromGutendex(book: GutendexBook): CatalogItem = CatalogItem(
            key = book.key,
            title = book.title,
            authors = book.authorNames,
            summary = book.summary,
            subjects = book.subjects,
            languages = book.languages,
            coverUrl = Formats.coverUrl(book.formats),
            downloadCount = book.downloadCount,
            copyright = book.copyright,
            source = BookSource.GUTENDEX,
            formats = book.formats,
        )
    }
}

/**
 * Halaman hasil seragam. [next] adalah kursor opaque:
 *  * Gutenberg -> URL absolut API yang sudah lolos cek tepercaya;
 *  * sumber lain -> kursor internal per sumber (`page=2`, `offset=20`, …).
 */
data class CatalogPage(
    val results: List<CatalogItem>,
    val next: String? = null,
)
