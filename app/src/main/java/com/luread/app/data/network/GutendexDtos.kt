package com.luread.app.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Model JSON Gutendex berdasarkan respons nyata yang direkam pada 2026-10-12
 * (lihat docs/gutendex-api-audit.md).
 *
 * Prinsip yang dipakai:
 *  * semua field diberi default aman karena katalog berubah dan bisa saja ada
 *    varian yang tidak pernah muncul di sampel;
 *  * `copyright` bertipe boolean pada 184/184 sampel tetapi tetap nullable
 *    untuk berjaga;
 *  * `formats` memakai nullable value karena key MIME boleh kosong.
 */

@Serializable
data class GutendexPage(
    val count: Int = 0,
    val next: String? = null,
    val previous: String? = null,
    val results: List<GutendexBook> = emptyList(),
)

@Serializable
data class GutendexBook(
    val id: Int,
    val title: String = "",
    @SerialName("authors") val authors: List<GutendexPerson> = emptyList(),
    @SerialName("summaries") val summaries: List<String> = emptyList(),
    @SerialName("editors") val editors: List<GutendexPerson> = emptyList(),
    @SerialName("translators") val translators: List<GutendexPerson> = emptyList(),
    @SerialName("subjects") val subjects: List<String> = emptyList(),
    @SerialName("bookshelves") val bookshelves: List<String> = emptyList(),
    @SerialName("languages") val languages: List<String> = emptyList(),
    @SerialName("copyright") val copyright: Boolean? = null,
    @SerialName("media_type") val mediaType: String = "Text",
    @SerialName("formats") val formats: Map<String, String?> = emptyMap(),
    @SerialName("download_count") val downloadCount: Int = 0,
) {
    val summary: String?
        get() = summaries.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    val authorNames: List<String>
        get() = authors.map { it.name }.filter { it.isNotBlank() }

    /** Kunci komposit buku (sumber:remote-id) untuk navigasi & basis data. */
    val key: String
        get() = "gutenberg:$id"
}

@Serializable
data class GutendexPerson(
    val name: String = "",
    @SerialName("birth_year") val birthYear: Int? = null,
    @SerialName("death_year") val deathYear: Int? = null,
) {
    val lifeSpan: String?
        get() = when {
            birthYear != null && deathYear != null -> "$birthYear–$deathYear"
            birthYear != null -> "b. $birthYear"
            deathYear != null -> "d. $deathYear"
            else -> null
        }
}

/**
 * Parameter katalog yang didukung Gutendex. Hanya parameter yang sudah diuji
 * langsung yang dimasukkan (lihat docs/gutendex-api-audit.md).
 */
data class CatalogQuery(
    val search: String? = null,
    val languages: List<String> = emptyList(),
    val topic: String? = null,
    val mimeType: String? = null,
    val page: Int? = null,
    /** Satu ID buku. `ids=1342` sudah diuji 200; multi-ID tidak dipakai (lihat audit). */
    val ids: Int? = null,
    val extraUrl: String? = null,
) {
    val isEmpty: Boolean
        get() = search.isNullOrBlank() && languages.isEmpty() &&
            topic.isNullOrBlank() && mimeType.isNullOrBlank() && extraUrl == null && ids == null
}
