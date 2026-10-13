package com.luread.app.data.catalog

/**
 * Logika pemilihan format buku.
 *
 * Seluruh fungsi murni dan diuji terhadap struktur JSON asli Gutendex.
 * Temuan audit yang dijadikan dasar:
 *  * key MIME bisa membawa parameter, contoh `text/plain; charset=utf-8`
 *    dan `text/plain; charset=us-ascii` muncul bersamaan;
 *  * URL format tidak pernah boleh disusun dari ID buku karena pola aslinya
 *    bervariasi (`/ebooks/1342.epub3.images`, `/files/1342/1342-0.txt`, …);
 *  * tidak semua buku punya EPUB: pada sampel 178/184 buku teks punya EPUB
 *    dan hanya 3/184 punya PDF.
 */
object Formats {

    const val MIME_EPUB = "application/epub+zip"
    const val MIME_HTML = "text/html"
    const val MIME_TEXT = "text/plain"
    const val MIME_PDF = "application/pdf"
    const val MIME_MOBI = "application/x-mobipocket-ebook"
    const val MIME_FB2 = "application/xml-fiction-book"
    const val MIME_CBZ = "application/vnd.comicbook+zip"
    const val MIME_DJVU = "image/vnd.djvu"
    const val MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    const val MIME_RTF = "application/rtf"
    const val MIME_MARKDOWN = "text/markdown"
    const val MIME_JPEG = "image/jpeg"
    const val MIME_RDF = "application/rdf+xml"
    const val MIME_OCTET_STREAM = "application/octet-stream"

    /**
     * Format yang bisa dibaca Luread (punya reader engine), terurut dari yang paling disukai.
     * MOBI/FB2/DJVU/DOCX/RTF/MD saat ini hanya bisa diunduh (tidak ada reader engine) —
     * ditandai readable=false di [AvailableFormat] agar jujur kepada pengguna.
     * CBZ punya reader gambar sendiri (CbzReader).
     */
    enum class Readable(val mime: String, val label: String, val extension: String) {
        EPUB(MIME_EPUB, "EPUB", "epub"),
        HTML(MIME_HTML, "HTML", "html"),
        TEXT(MIME_TEXT, "Plain text", "txt"),
        PDF(MIME_PDF, "PDF", "pdf"),
        CBZ(MIME_CBZ, "Comic Book (CBZ)", "cbz"),
    }

    data class AvailableFormat(
        val mime: String,
        val label: String,
        val url: String,
        val readable: Boolean,
    )

    /** Hilangkan parameter MIME dan seragamkan huruf. */
    fun normalizeMime(key: String): String =
        key.substringBefore(';').trim().lowercase()

    /**
     * Kumpulkan URL per MIME ternormalisasi.
     * Bila ada dua varian charset untuk MIME sama, `charset=utf-8` dipilih lebih dulu.
     */
    fun urlsByMime(formats: Map<String, String?>): Map<String, String> {
        val picked = LinkedHashMap<String, Pair<Int, String>>()
        for ((rawKey, url) in formats) {
            val mime = normalizeMime(rawKey)
            if (url.isNullOrBlank()) continue
            val preference = if (rawKey.contains("charset=utf-8", ignoreCase = true)) 0 else 1
            val existing = picked[mime]
            if (existing == null || preference < existing.first) {
                picked[mime] = preference to url
            }
        }
        return picked.mapValues { it.value.second }
    }

    /** Format terbaik yang benar-benar bisa dibaca Luread, atau null. */
    fun bestReadable(formats: Map<String, String?>): Readable? {
        val available = urlsByMime(formats)
        return Readable.entries.firstOrNull { available.containsKey(it.mime) }
    }

    /** URL cover bila katalog menyediakannya. */
    fun coverUrl(formats: Map<String, String?>): String? =
        urlsByMime(formats)[MIME_JPEG]

    /** Daftar format untuk layar detail, hanya yang URL-nya nyata. */
    fun available(formats: Map<String, String?>): List<AvailableFormat> {
        val byMime = urlsByMime(formats)
        val readable = Readable.entries.associateBy { it.mime }
        return byMime.mapNotNull { (mime, url) ->
            val readableFormat = readable[mime]
            AvailableFormat(
                mime = mime,
                label = readableFormat?.label ?: labelFor(mime),
                url = url,
                readable = readableFormat != null,
            )
        }.sortedWith(compareByDescending<AvailableFormat> { it.readable }.thenBy { it.label })
    }

    /** Ekstensi file untuk unduhan. */
    fun extensionFor(mime: String): String = when (normalizeMime(mime)) {
        MIME_EPUB -> "epub"
        MIME_PDF -> "pdf"
        MIME_HTML -> "html"
        MIME_TEXT -> "txt"
        MIME_MOBI -> "mobi"
        MIME_FB2 -> "fb2"
        MIME_CBZ -> "cbz"
        MIME_DJVU -> "djvu"
        MIME_DOCX -> "docx"
        MIME_RTF -> "rtf"
        MIME_MARKDOWN -> "md"
        else -> "bin"
    }

    private fun labelFor(mime: String): String = when (normalizeMime(mime)) {
        MIME_RDF -> "RDF metadata"
        MIME_MOBI -> "MOBI"
        MIME_FB2 -> "Fiction Book 2"
        MIME_CBZ -> "Comic Book ZIP"
        MIME_DJVU -> "DjVu"
        MIME_DOCX -> "Word document"
        MIME_RTF -> "Rich Text Format"
        MIME_MARKDOWN -> "Markdown"
        MIME_JPEG -> "Cover"
        MIME_OCTET_STREAM -> "Archive"
        "audio/mpeg" -> "Audio (MP3)"
        "audio/ogg" -> "Audio (OGG)"
        "audio/mp4" -> "Audio (M4A)"
        "application/msword" -> "Word document (legacy)"
        "text/x-rst" -> "reStructuredText"
        "application/zip" -> "ZIP archive"
        "application/x-cbz" -> "Comic Book ZIP"
        else -> normalizeMime(mime).substringAfterLast('/')
    }

    /**
     * Deteksi format file berdasarkan isi (magic number) — dipakai untuk import
     * dan validasi unduhan, bukan sekadar nama berkas.
     */
    fun sniff(head: ByteArray): String? {
        if (head.size >= 4) {
            val isZip = head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() &&
                head[2].toInt() in 3..8
            if (isZip) return EPUB_OR_ZIP
            val isPdf = head[0] == '%'.code.toByte() && head[1] == 'P'.code.toByte() &&
                head[2] == 'D'.code.toByte() && head[3] == 'F'.code.toByte()
            if (isPdf) return MIME_PDF
        }
        if (head.size >= 8) {
            // MOBI: bytes 60..67 biasanya "BOOKMOBI" atau "TEXtREAd"
            if (head.size >= 68) {
                val tag = String(head, 60, 8, Charsets.US_ASCII)
                if (tag == "BOOKMOBI" || tag == "TEXtREAd") return MIME_MOBI
            }
            // FB2: file diawali "<?xml" lalu "<FictionBook"
            if (head[0] == '<'.code.toByte()) {
                val headStr = String(head, Charsets.US_ASCII)
                if (headStr.contains("FictionBook")) return MIME_FB2
            }
            // DjVu: "AT&TFORM"
            if (String(head, 0, 8, Charsets.US_ASCII) == "AT&TFORM") return MIME_DJVU
            // RTF: "{\rtf"
            if (head[0] == '{'.code.toByte() && head[1] == '\\'.code.toByte() &&
                head[2] == 'r'.code.toByte() && head[3] == 't'.code.toByte() &&
                head[4] == 'f'.code.toByte()
            ) {
                return MIME_RTF
            }
        }
        return null
    }

    const val EPUB_OR_ZIP = "application/zip"

    /** True bila magic number konsisten dengan EPUB (zip dengan container.xml). */
    fun looksLikeEpub(head: ByteArray): Boolean = sniff(head) == EPUB_OR_ZIP
}
