package com.luread.app.data.catalog

/**
 * Pemetaan kode bahasa UI (ISO 639-1) ke ISO 639-2 yang dimengerti
 * Open Library (`language=eng`) dan Internet Archive (`language:eng`).
 *
 * Semua pasangan diuji langsung pada 2026-10-13; lihat
 * docs/book-apis-audit.md.
 */
object CatalogLanguages {
    val ISO_639_2 = mapOf(
        "en" to "eng",
        "fr" to "fre",
        "de" to "ger",
        "es" to "spa",
        "it" to "ita",
        "nl" to "dut",
        "pt" to "por",
        "ru" to "rus",
        "zh" to "zho",
        "fi" to "fin",
        "id" to "ind",
    )

    /** Terjemahkan kode UI pertama yang dikenal, atau null. */
    fun isoFor(codes: List<String>): String? =
        codes.firstNotNullOfOrNull { ISO_639_2[it] }
}
