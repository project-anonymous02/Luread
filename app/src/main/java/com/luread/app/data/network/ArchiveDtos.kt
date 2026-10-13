package com.luread.app.data.network

import com.luread.app.data.catalog.BookSource
import com.luread.app.data.catalog.CatalogItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Model JSON Internet Archive berdasarkan uji langsung 2026-10-13
 * (docs/book-apis-audit.md):
 *  * `advancedsearch.php?...&output=json` membalas `{response: {docs}}`;
 *  * field `title`/`creator`/`language`/`description` bisa berupa string
 *    ATAU array -> dinormalisasi lewat [elementText]/[elementTextList];
 *  * `format` file pada `metadata/{id}` antara lain `EPUB`, `Text PDF`,
 *    `DjVuTXT` (dipakai ArchiveFormats untuk memilih unduhan);
 *  * bahasa tersimpan sebagai kode ISO 639-2 (`eng`, `ind`, `fre`).
 */
@Serializable
data class ArchiveSearchResponse(
    val response: ArchiveDocs = ArchiveDocs(),
)

@Serializable
data class ArchiveDocs(
    val numFound: Int = 0,
    val start: Int = 0,
    val docs: List<ArchiveDoc> = emptyList(),
)

@Serializable
data class ArchiveDoc(
    val identifier: String = "",
    val title: JsonElement? = null,
    val creator: JsonElement? = null,
    val description: JsonElement? = null,
    val language: JsonElement? = null,
    val subject: JsonElement? = null,
    val downloads: Int? = null,
    val year: Int? = null,
) {
    fun toItem(): CatalogItem = CatalogItem(
        key = BookSource.INTERNET_ARCHIVE.prefix + identifier,
        title = title.elementText() ?: identifier,
        authors = creator.elementTextList(),
        summary = description.elementText()?.take(600),
        subjects = subject.elementTextList(),
        languages = language.elementTextList(),
        coverUrl = "https://archive.org/services/img/$identifier",
        downloadCount = downloads,
        copyright = null,
        source = BookSource.INTERNET_ARCHIVE,
        canonicalUrl = "https://archive.org/details/$identifier",
    )
}

@Serializable
data class ArchiveMetadata(
    val metadata: ArchiveItemMetadata? = null,
    val files: List<ArchiveFile> = emptyList(),
)

@Serializable
data class ArchiveItemMetadata(
    val title: JsonElement? = null,
    val description: JsonElement? = null,
    val creator: JsonElement? = null,
    val language: JsonElement? = null,
    val subject: JsonElement? = null,
)

@Serializable
data class ArchiveFile(
    val name: String = "",
    val format: String? = null,
    val size: Long? = null,
    val source: String? = null,
)

/** Ambil teks pertama dari string/berkas tunggal atau array berkas tunggal. */
internal fun JsonElement?.elementText(): String? = when (this) {
    is JsonNull -> null
    is JsonPrimitive -> content.trim().takeIf { it.isNotEmpty() }
    is JsonArray -> firstOrNull()?.elementText()
    else -> null
}

/** Ambil semua teks dari string tunggal atau array. */
internal fun JsonElement?.elementTextList(): List<String> = when (this) {
    is JsonNull -> emptyList()
    is JsonPrimitive -> listOfNotNull(content.trim().takeIf { it.isNotEmpty() })
    is JsonArray -> mapNotNull { it.elementText() }
    else -> emptyList()
}
