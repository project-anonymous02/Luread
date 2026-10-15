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
    // LUREAD-FEAT: RAR terdeteksi supaya unduhan/impor bisa menolak dengan
    // pesan jelas — Luread tidak punya engine RAR (hanya ZipFile).
    const val MIME_RAR = "application/vnd.rar"

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
     *
     * LUREAD-FIX: MOBI butuh minimal 68 byte (tag "BOOKMOBI" di offset 60),
     * jadi pemanggil wajib membaca setidaknya 72 byte; penambahan deteksi
     * RAR dan HTML agar file dengan ekstensi salah tetap teridentifikasi.
     */
    fun sniff(head: ByteArray): String? {
        if (head.size >= 4) {
            val isZip = head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() &&
                head[2].toInt() in 3..8
            if (isZip) return EPUB_OR_ZIP
            val isPdf = head[0] == '%'.code.toByte() && head[1] == 'P'.code.toByte() &&
                head[2] == 'D'.code.toByte() && head[3] == 'F'.code.toByte()
            if (isPdf) return MIME_PDF
            // RAR: "Rar!" diikuti 1A 07 (RAR4) atau 1A 07 01 00 (RAR5).
            if (head.size >= 6 &&
                head[0] == 'R'.code.toByte() && head[1] == 'a'.code.toByte() &&
                head[2] == 'r'.code.toByte() && head[3] == '!'.code.toByte() &&
                head[4] == 0x1A.toByte()
            ) {
                return MIME_RAR
            }
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
                // HTML: doctype/html awal (kasus ekstensi .txt/.bin yang isinya web).
                val lower = headStr.lowercase()
                if (lower.contains("<!doctype html") || lower.contains("<html")) return MIME_HTML
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

    // ------------------------------------------------------------------
    // LUREAD-FEAT: logika keputusan impor (fungsi murni, diuji unit test;
    // LibraryViewModel hanya membungkusnya dengan pesan galat lokal).
    // ------------------------------------------------------------------

    /**
     * MIME yang boleh masuk perpustakaan lewat impor (punya reader atau
     * minimal tersimpan untuk dibuka kemudian).
     */
    val IMPORTABLE_MIMES: Set<String> = setOf(
        MIME_EPUB, MIME_HTML, MIME_TEXT, MIME_PDF, MIME_CBZ,
        MIME_MOBI, MIME_FB2, MIME_DJVU, MIME_RTF, MIME_DOCX, MIME_MARKDOWN,
    )

    /**
     * True bila file harus diekstrak sebagai arsip ZIP polos, bukan diimpor
     * langsung: ekstensi `zip`, ATAU magic-nya zip tetapi ekstensinya bukan
     * `epub`/`cbz`/`cbr`/`docx` (empat format berbasis zip yang punya
     * identitas sendiri — mis. `buku.zip.txt` yang isinya arsip).
     */
    fun isPlainZipArchive(nameExt: String, magic: ByteArray): Boolean =
        nameExt.lowercase() == "zip" ||
            (sniff(magic) == EPUB_OR_ZIP &&
                nameExt.lowercase() !in setOf("epub", "cbz", "cbr", "docx"))

    /**
     * Tentukan MIME impor dari ekstensi + isi file. Urutan keputusan
     * (dipakai [com.luread.app.ui.LibraryViewModel] untuk impor langsung
     * dan ekstrak ZIP):
     *  * ekstensi `cbz`/`cbr` menang untuk arsip komik (keduanya berbasis zip);
     *  * magic number menang atas ekstensi (file `.txt` berisi HTML → HTML);
     *  * tanpa magic yang dikenal, ekstensi menjadi harapan terakhir;
     *  * null = format tidak didukung.
     */
    fun resolveImportMime(nameExt: String, magic: ByteArray): String? {
        val ext = nameExt.lowercase()
        // Format berbasis zip dengan identitas sendiri: ekstensi menang.
        if (ext == "cbz" || ext == "cbr") return MIME_CBZ
        if (ext == "docx") return MIME_DOCX
        val sniffed = sniff(magic) ?: when (ext) {
            "epub" -> MIME_EPUB
            "pdf" -> MIME_PDF
            "txt", "text" -> MIME_TEXT
            "html", "htm" -> MIME_HTML
            "mobi", "prc", "azw", "azw3" -> MIME_MOBI
            "fb2" -> MIME_FB2
            "djvu" -> MIME_DJVU
            "docx" -> MIME_DOCX
            "rtf" -> MIME_RTF
            "md", "markdown" -> MIME_MARKDOWN
            else -> return null
        }
        return when (sniffed) {
            EPUB_OR_ZIP -> MIME_EPUB
            else -> sniffed
        }
    }
}
