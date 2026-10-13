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
        "ja" to "jpn",
        "ko" to "kor",
        "ar" to "ara",
        "hi" to "hin",
        "sv" to "swe",
        "da" to "dan",
        "no" to "nor",
        "pl" to "pol",
        "tr" to "tur",
        "vi" to "vie",
        "th" to "tha",
        "el" to "gre",
        "cs" to "cze",
        "hu" to "hun",
        "ro" to "rum",
        "uk" to "ukr",
        "he" to "heb",
        "fa" to "per",
        "bn" to "ben",
        "ta" to "tam",
        "te" to "tel",
        "mr" to "mar",
        "gu" to "guj",
        "kn" to "kan",
        "ml" to "mal",
        "pa" to "pan",
        "ur" to "urd",
        "sw" to "swa",
        "af" to "afr",
        "ca" to "cat",
        "eu" to "baq",
        "gl" to "glg",
        "is" to "ice",
        "ga" to "gle",
        "cy" to "wel",
        "sq" to "alb",
        "hy" to "arm",
        "ka" to "geo",
        "kk" to "kaz",
        "uz" to "uzb",
        "az" to "aze",
        "mn" to "mon",
        "ne" to "nep",
        "si" to "sin",
        "my" to "bur",
        "km" to "khm",
        "lo" to "lao",
        "am" to "amh",
        "so" to "som",
        "ha" to "hau",
        "yo" to "yor",
        "ig" to "ibo",
        "zu" to "zul",
        "xh" to "xho",
    )

    /** Terjemahkan kode UI pertama yang dikenal, atau null. */
    fun isoFor(codes: List<String>): String? =
        codes.firstNotNullOfOrNull { ISO_639_2[it] }
}
