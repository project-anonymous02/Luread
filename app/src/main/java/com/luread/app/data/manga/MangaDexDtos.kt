package com.luread.app.data.manga

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Model JSON MangaDex (uji dokumentasi resmi 2026-10-13,
 * https://api.mangadex.org/docs).
 *
 * Endpoint kunci:
 *  * `GET /manga?limit=20&title=…&contentRating[]=safe&includes[]=cover_art&includes[]=author`
 *  * `GET /manga/{id}/feed?limit=100&translatedLanguage[]=id&order[chapter]=asc`
 *  * `GET /at-home/server/{chapterId}` -> baseUrl + hash + data[] (URL gambar)
 *
 * Semua field diberi default aman karena API bisa berubah.
 */
@Serializable
data class MangaDexListResponse(
    val result: String = "",
    val data: List<MangaDexManga> = emptyList(),
    val limit: Int = 0,
    val offset: Int = 0,
    val total: Int = 0,
)

/** Respon tunggal `GET /manga/{id}`. */
@Serializable
data class MangaDexMangaResponse(
    val result: String = "",
    val data: MangaDexManga? = null,
)

@Serializable
data class MangaDexManga(
    val id: String = "",
    val type: String = "manga",
    val attributes: MangaDexMangaAttributes? = null,
    val relationships: List<MangaDexRelationship> = emptyList(),
) {
    val title: String
        get() = attributes?.title?.values?.firstOrNull()?.trim().orEmpty().ifBlank { "Manga" }

    val description: String?
        get() = attributes?.description?.values?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    val coverUrl: String?
        get() = relationships.firstOrNull { it.type == "cover_art" }
            ?.attributes?.fileName
            ?.let { "https://uploads.mangadex.org/covers/$id/$it.256.jpg" }

    val authorNames: List<String>
        get() = relationships.filter { it.type == "author" }
            .mapNotNull { it.attributes?.name?.trim()?.takeIf { n -> n.isNotEmpty() } }

    val tags: List<String>
        get() = attributes?.tags?.mapNotNull { it.attributes?.name?.en?.trim() }.orEmpty()

    val status: String?
        get() = attributes?.status

    val originalLanguage: String?
        get() = attributes?.originalLanguage
}

@Serializable
data class MangaDexMangaAttributes(
    val title: Map<String, String>? = null,
    val description: Map<String, String>? = null,
    val status: String? = null,
    val year: Int? = null,
    @SerialName("originalLanguage") val originalLanguage: String? = null,
    val tags: List<MangaDexTag> = emptyList(),
)

@Serializable
data class MangaDexTag(
    val id: String = "",
    val attributes: MangaDexTagAttributes? = null,
)

@Serializable
data class MangaDexTagAttributes(
    val name: MangaDexLocalizedText? = null,
)

@Serializable
data class MangaDexLocalizedText(
    val en: String? = null,
)

@Serializable
data class MangaDexRelationship(
    val id: String = "",
    val type: String = "",
    val attributes: MangaDexRelationshipAttributes? = null,
)

@Serializable
data class MangaDexRelationshipAttributes(
    val fileName: String? = null,
    val name: String? = null,
)

// ------------------------------------------------------------------
// Feed chapter
// ------------------------------------------------------------------

@Serializable
data class MangaDexFeedResponse(
    val result: String = "",
    val data: List<MangaDexChapter> = emptyList(),
    val limit: Int = 0,
    val offset: Int = 0,
    val total: Int = 0,
)

@Serializable
data class MangaDexChapter(
    val id: String = "",
    val type: String = "chapter",
    val attributes: MangaDexChapterAttributes? = null,
)

@Serializable
data class MangaDexChapterAttributes(
    val title: String? = null,
    val chapter: String? = null,
    val volume: String? = null,
    @SerialName("translatedLanguage") val translatedLanguage: String? = null,
    val pages: Int? = null,
    @SerialName("publishAt") val publishAt: String? = null,
)

// ------------------------------------------------------------------
// At-home server (gambar)
// ------------------------------------------------------------------

@Serializable
data class MangaDexAtHome(
    val result: String = "",
    @SerialName("baseUrl") val baseUrl: String = "",
    val chapter: MangaDexAtHomeChapter? = null,
)

@Serializable
data class MangaDexAtHomeChapter(
    val hash: String = "",
    val data: List<String> = emptyList(),
    @SerialName("dataSaver") val dataSaver: List<String> = emptyList(),
) {
    /** URL gambar halaman ke-[index] (kualitas asli). */
    fun pageUrl(baseUrl: String, index: Int, saver: Boolean = false): String? {
        val files = if (saver) dataSaver else data
        val file = files.getOrNull(index) ?: return null
        val quality = if (saver) "data-saver" else "data"
        return "$baseUrl/$quality/$hash/$file"
    }
}
