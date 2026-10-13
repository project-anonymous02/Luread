package com.luread.app.data.catalog

import com.luread.app.data.network.ArchiveFile
import java.net.URLEncoder

/**
 * Memilih berkas unduhan dari daftar file item Internet Archive.
 *
 * Aturan diverifikasi terhadap metadata nyata (docs/book-apis-audit.md):
 *  * `prideprejudice01aust` memuat `*.epub` (EPUB), `*.pdf` (Text PDF),
 *    `*_djvu.txt` (DjVuTXT) — ketiganya masuk sebagai kandidat;
 *  * preferensi format yang bisa dibaca Luread: EPUB > PDF > teks polos;
 *  * PDF turunan (`*_text.pdf`) hanya dipakai bila tidak ada PDF asli;
 *  * URL unduhan disusun dari nama file yang di-encode per segmen
 *    (nama IA boleh mengandung spasi).
 */
object ArchiveFormats {

    fun downloadUrl(identifier: String, fileName: String): String {
        val encoded = fileName.split("/").joinToString("/") { segment ->
            URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }
        return "https://archive.org/download/$identifier/$encoded"
    }

    /** Pilih URL unduhan per MIME; map kosong bila tidak ada kandidat. */
    fun pick(files: List<ArchiveFile>, identifier: String): Map<String, String> {
        val epub = files.firstOrNull { f ->
            f.name.endsWith(".epub", ignoreCase = true) ||
            f.format.equals("EPUB", ignoreCase = true)
        }
        // PDF asli dulu; turunan teks (`*_text.pdf`) hanya bila tak ada lain.
        val pdf = files.firstOrNull { f ->
            f.name.endsWith(".pdf", ignoreCase = true) && !f.name.endsWith("_text.pdf", ignoreCase = true)
        } ?: files.firstOrNull { f ->
            f.format.equals("Text PDF", ignoreCase = true)
        } ?: files.firstOrNull { f -> f.name.endsWith(".pdf", ignoreCase = true) }
        val text = files.firstOrNull { f ->
            f.name.endsWith("_djvu.txt", ignoreCase = true) ||
            f.format.equals("DjVuTXT", ignoreCase = true)
        }

        val picked = LinkedHashMap<String, String>()
        epub?.let { picked[Formats.MIME_EPUB] = downloadUrl(identifier, it.name) }
        pdf?.let { picked[Formats.MIME_PDF] = downloadUrl(identifier, it.name) }
        text?.let { picked[Formats.MIME_TEXT] = downloadUrl(identifier, it.name) }
        return picked
    }
}
