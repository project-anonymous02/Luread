package com.luread.app.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Uji fitur dukungan file: keputusan impor per format (magic + ekstensi),
 * keputusan ekstrak ZIP polos, dan ekstraksi ZIP dengan entri nyata —
 * memakai arsip ZIP sungguhan yang dibangun saat test.
 *
 * Logika yang diuji adalah fungsi murni [Formats] yang menjadi satu-satunya
 * sumber keputusan bagi `LibraryViewModel` (impor langsung & ekstrak ZIP).
 */
class SupportedFormatsTest {

    private fun head(bytes: ByteArray): ByteArray =
        bytes.copyOf(minOf(128, bytes.size))

    private fun zipBytes(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, data) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun mobiBytes(): ByteArray {
        val b = ByteArray(68)
        "BOOKMOBI".toByteArray().copyInto(b, 60)
        return b
    }

    private fun rarBytes(): ByteArray =
        byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00, 0x00)

    // ------------------------------------------------------------------
    // 1. Impor langsung: seluruh format yang didukung
    // ------------------------------------------------------------------

    @Test
    fun `semua format didukung terdeteksi dari ekstensi yang benar`() {
        val cases = listOf(
            Triple("buku.epub", zipBytes("mimetype" to "application/epub+zip".toByteArray()), Formats.MIME_EPUB),
            Triple("laporan.pdf", "%PDF-1.7\n1 0 obj".toByteArray(), Formats.MIME_PDF),
            Triple("catatan.txt", "halo dunia".toByteArray(), Formats.MIME_TEXT),
            Triple("halaman.html", "<!DOCTYPE html><html>".toByteArray(), Formats.MIME_HTML),
            Triple("novel.mobi", mobiBytes(), Formats.MIME_MOBI),
            Triple("cerita.fb2", "<?xml?><FictionBook xmlns='x'>".toByteArray(), Formats.MIME_FB2),
            Triple("peta.djvu", "AT&TFORM".toByteArray() + byteArrayOf(0, 0), Formats.MIME_DJVU),
            Triple("surat.rtf", "{\\rtf1\\ansi teks}".toByteArray(), Formats.MIME_RTF),
            Triple("dokumen.docx", zipBytes("[Content_Types].xml" to "<Types/>".toByteArray()), Formats.MIME_DOCX),
            Triple("bacaan.md", "# Judul".toByteArray(), Formats.MIME_MARKDOWN),
            Triple("komik.cbz", zipBytes("001.jpg" to byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())), Formats.MIME_CBZ),
        )
        for ((name, bytes, expected) in cases) {
            val ext = name.substringAfterLast('.')
            assertEquals("gagal untuk $name", expected, Formats.resolveImportMime(ext, head(bytes)))
        }
    }

    @Test
    fun `magic number menang atas ekstensi yang salah`() {
        // File .txt yang isinya HTML → terbaca sebagai HTML.
        assertEquals(
            Formats.MIME_HTML,
            Formats.resolveImportMime("txt", head("<html><body>".toByteArray())),
        )
        // File .xyz yang isinya PDF → terbaca sebagai PDF.
        assertEquals(
            Formats.MIME_PDF,
            Formats.resolveImportMime("xyz", head("%PDF-1.4".toByteArray())),
        )
        // Isi zip tanpa ekstensi format → diperlakukan sebagai EPUB/zip.
        assertEquals(
            Formats.MIME_EPUB,
            Formats.resolveImportMime("bin", head(zipBytes("a" to "b".toByteArray()))),
        )
    }

    @Test
    fun `format tak dikenal ditolak null`() {
        assertNull(Formats.resolveImportMime("xyz", head("teks biasa saja".toByteArray())))
        assertNull(Formats.resolveImportMime("", head("teks biasa saja".toByteArray())))
        assertNull(Formats.resolveImportMime("xyz", ByteArray(0)))
    }

    @Test
    fun `cbz dan docx menang atas isi zip yang sama dengan epub`() {
        val zip = head(zipBytes("isi" to "data".toByteArray()))
        assertEquals(Formats.MIME_CBZ, Formats.resolveImportMime("cbz", zip))
        assertEquals(Formats.MIME_CBZ, Formats.resolveImportMime("cbr", zip))
        assertEquals(Formats.MIME_DOCX, Formats.resolveImportMime("docx", zip))
        assertEquals(Formats.MIME_EPUB, Formats.resolveImportMime("epub", zip))
    }

    // ------------------------------------------------------------------
    // 2. Keputusan ZIP polos: ekstrak vs impor langsung
    // ------------------------------------------------------------------

    @Test
    fun `isPlainZipArchive memutuskan ekstrak hanya untuk arsip polos`() {
        val zipMagic = head(zipBytes("x" to "y".toByteArray()))
        val pdfMagic = head("%PDF-1.7".toByteArray())
        val textMagic = head("hanya teks".toByteArray())

        // Ekstrak:
        assertTrue(Formats.isPlainZipArchive("zip", zipMagic))
        assertTrue(Formats.isPlainZipArchive("ZIP", zipMagic))
        assertTrue("arsip menyamar harus diekstrak", Formats.isPlainZipArchive("txt", zipMagic))
        // Impor langsung (punya identitas format sendiri):
        assertFalse(Formats.isPlainZipArchive("epub", zipMagic))
        assertFalse(Formats.isPlainZipArchive("cbz", zipMagic))
        assertFalse(Formats.isPlainZipArchive("cbr", zipMagic))
        assertFalse("docx jangan disedot sebagai arsip polos", Formats.isPlainZipArchive("docx", zipMagic))
        assertFalse(Formats.isPlainZipArchive("pdf", pdfMagic))
        assertFalse(Formats.isPlainZipArchive("txt", textMagic))
    }

    // ------------------------------------------------------------------
    // 3. Ekstraksi ZIP polos — arsip sungguhan dengan entri campuran
    // ------------------------------------------------------------------

    @Test
    fun `ekstraksi zip polos hanya mengimpor entri yang didukung`() {
        // Arsitektur sama persis dengan LibraryViewModel.importZipContents:
        // baca tiap entri -> head 128 byte -> resolveImportMime -> cek IMPORTABLE.
        val archive = zipBytes(
            "buku/note.txt" to "isi catatan".toByteArray(),
            "halaman.html" to "<html>halo</html>".toByteArray(),
            "inner.epub" to zipBytes("mimetype" to "application/epub+zip".toByteArray()),
            "sampul.jpg" to byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()),
            "acak.xyz" to "bukan buku".toByteArray(),
            "folder/" to ByteArray(0),
        )

        val imported = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val data = zip.readBytes()
                    val ext = entry.name.substringAfterLast('.', "")
                    val mime = runCatching { Formats.resolveImportMime(ext, head(data)) }.getOrNull()
                    if (mime != null && mime in Formats.IMPORTABLE_MIMES) {
                        imported += "${entry.name}=$mime"
                    } else {
                        skipped += entry.name
                    }
                }
                entry = zip.nextEntry
            }
        }

        assertEquals(
            listOf(
                "buku/note.txt=${Formats.MIME_TEXT}",
                "halaman.html=${Formats.MIME_HTML}",
                "inner.epub=${Formats.MIME_EPUB}",
            ),
            imported,
        )
        assertEquals(listOf("sampul.jpg", "acak.xyz"), skipped)
    }

    @Test
    fun `zip berisi komik cbz diekstrak sebagai cbz ketika ekstensinya cbz`() {
        val cbz = zipBytes("001.jpg" to byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
        // File .cbz adalah arsip zip tapi punya identitas → impor langsung.
        assertFalse(Formats.isPlainZipArchive("cbz", head(cbz)))
        assertEquals(Formats.MIME_CBZ, Formats.resolveImportMime("cbz", head(cbz)))
        // Sedangkan .zip berisi gambar yang sama → diekstrak, lalu entri
        // gambar tidak masuk (bukan format buku).
        assertTrue(Formats.isPlainZipArchive("zip", head(cbz)))
    }

    // ------------------------------------------------------------------
    // 4. RAR ditolak di semua jalur
    // ------------------------------------------------------------------

    @Test
    fun `rar terdeteksi dan tidak pernah masuk daftar impor`() {
        val rar = head(rarBytes())
        assertEquals(Formats.MIME_RAR, Formats.sniff(rar))
        // Jalur impor langsung: LibraryViewModel melempar import_error_rar
        // saat sniff == MIME_RAR (syarat diuji di sini).
        assertEquals(Formats.MIME_RAR, Formats.resolveImportMime("rar", rar))
        // Jalur entri ZIP: MIME_RAR tidak ada di IMPORTABLE_MIMES → dilewati.
        assertFalse(Formats.IMPORTABLE_MIMES.contains(Formats.MIME_RAR))
        // Ekstensi .cbr yang isinya RAR tetap ditolak oleh guard magic di ViewModel.
        assertEquals(Formats.MIME_RAR, Formats.sniff(rarBytes().let { head(it) }))
    }

    // ------------------------------------------------------------------
    // 5. Kelengkapan daftar
    // ------------------------------------------------------------------

    @Test
    fun `IMPORTABLE_MIMES memuat semua format yang bisa diimpor`() {
        assertEquals(
            setOf(
                Formats.MIME_EPUB, Formats.MIME_HTML, Formats.MIME_TEXT, Formats.MIME_PDF,
                Formats.MIME_CBZ, Formats.MIME_MOBI, Formats.MIME_FB2, Formats.MIME_DJVU,
                Formats.MIME_RTF, Formats.MIME_DOCX, Formats.MIME_MARKDOWN,
            ),
            Formats.IMPORTABLE_MIMES,
        )
    }

    @Test
    fun `semua format impor punya ekstensi file sendiri bukan bin`() {
        for (mime in Formats.IMPORTABLE_MIMES) {
            val ext = Formats.extensionFor(mime)
            assertTrue("extensionFor($mime) tidak boleh bin", ext != "bin" && ext.isNotEmpty())
        }
        assertEquals("epub", Formats.extensionFor(Formats.MIME_EPUB))
        assertEquals("cbz", Formats.extensionFor(Formats.MIME_CBZ))
        assertEquals("docx", Formats.extensionFor(Formats.MIME_DOCX))
    }

    @Test
    fun `sniff mengenali epb asli dari arsip zip sungguhan`() {
        val epub = zipBytes(
            "mimetype" to "application/epub+zip".toByteArray(),
            "META-INF/container.xml" to "<container/>".toByteArray(),
        )
        assertEquals(Formats.EPUB_OR_ZIP, Formats.sniff(head(epub)))
        assertTrue(Formats.looksLikeEpub(head(epub)))
        assertEquals(Formats.MIME_EPUB, Formats.resolveImportMime("epub", head(epub)))
    }
}
