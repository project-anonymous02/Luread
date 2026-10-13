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
        // Format tambahan yang didukung Luread untuk diunduh/dibuka.
        val mobi = files.firstOrNull { f ->
            f.name.endsWith(".mobi", ignoreCase = true) ||
            f.format.equals("MOBI", ignoreCase = true) ||
            f.format.equals("Mobipocket", ignoreCase = true)
        }
        val azw3 = files.firstOrNull { f ->
            f.name.endsWith(".azw3", ignoreCase = true) ||
            f.name.endsWith(".azw", ignoreCase = true) ||
            f.format.equals("Kindle for PD", ignoreCase = true) ||
            f.format.equals("Amazon Kindle", ignoreCase = true)
        }
        val fb2 = files.firstOrNull { f ->
            f.name.endsWith(".fb2", ignoreCase = true) ||
            f.format.equals("FictionBook 2", ignoreCase = true) ||
            f.format.equals("FictionBook2", ignoreCase = true)
        }
        val djvu = files.firstOrNull { f ->
            f.name.endsWith(".djvu", ignoreCase = true) ||
            f.format.equals("DjVu", ignoreCase = true)
        }
        val cbz = files.firstOrNull { f ->
            f.name.endsWith(".cbz", ignoreCase = true) ||
            f.format.equals("Comic Book ZIP", ignoreCase = true)
        }
        val cbr = files.firstOrNull { f ->
            f.name.endsWith(".cbr", ignoreCase = true) ||
            f.format.equals("Comic Book RAR", ignoreCase = true)
        }
        val rtf = files.firstOrNull { f ->
            f.name.endsWith(".rtf", ignoreCase = true) ||
            f.format.equals("Rich Text Format", ignoreCase = true)
        }
        val docx = files.firstOrNull { f ->
            f.name.endsWith(".docx", ignoreCase = true) ||
            f.format.equals("Microsoft Word", ignoreCase = true)
        }
        val html = files.firstOrNull { f ->
            f.name.endsWith(".html", ignoreCase = true) ||
            f.format.equals("HTML", ignoreCase = true)
        }
        val textUtf8 = files.firstOrNull { f ->
            f.name.endsWith(".txt", ignoreCase = true) && !f.name.endsWith("_djvu.txt", ignoreCase = true) &&
            f.format?.contains("Text", ignoreCase = true) == true
        }
        val md = files.firstOrNull { f ->
            f.name.endsWith(".md", ignoreCase = true) ||
            f.name.endsWith(".markdown", ignoreCase = true)
        }

        val picked = LinkedHashMap<String, String>()
        epub?.let { picked[Formats.MIME_EPUB] = downloadUrl(identifier, it.name) }
        pdf?.let { picked[Formats.MIME_PDF] = downloadUrl(identifier, it.name) }
        text?.let { picked[Formats.MIME_TEXT] = downloadUrl(identifier, it.name) }
        textUtf8?.let { picked.putIfAbsent(Formats.MIME_TEXT, downloadUrl(identifier, it.name)) }
        html?.let { picked[Formats.MIME_HTML] = downloadUrl(identifier, it.name) }
        mobi?.let { picked[Formats.MIME_MOBI] = downloadUrl(identifier, it.name) }
        azw3?.let { picked[Formats.MIME_MOBI] = downloadUrl(identifier, it.name) }
        fb2?.let { picked[Formats.MIME_FB2] = downloadUrl(identifier, it.name) }
        djvu?.let { picked[Formats.MIME_DJVU] = downloadUrl(identifier, it.name) }
        cbz?.let { picked[Formats.MIME_CBZ] = downloadUrl(identifier, it.name) }
        cbr?.let { picked[Formats.MIME_CBZ] = downloadUrl(identifier, it.name) }
        rtf?.let { picked[Formats.MIME_RTF] = downloadUrl(identifier, it.name) }
        docx?.let { picked[Formats.MIME_DOCX] = downloadUrl(identifier, it.name) }
        md?.let { picked[Formats.MIME_MARKDOWN] = downloadUrl(identifier, it.name) }
        return picked
    }
}
