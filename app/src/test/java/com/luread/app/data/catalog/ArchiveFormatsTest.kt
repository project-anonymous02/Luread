package com.luread.app.data.catalog

import com.luread.app.data.network.ArchiveFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveFormatsTest {

    private fun file(name: String, format: String? = null) = ArchiveFile(name = name, format = format)

    @Test
    fun `pilih epub pdf dan teks sesuai aturan preferensi`() {
        val files = listOf(
            file("book_djvu.txt", "DjVuTXT"),
            file("book_text.pdf", "Text PDF"),
            file("book.pdf", "Image Container PDF"),
            file("book.epub", "EPUB"),
        )
        val picked = ArchiveFormats.pick(files, "book7777")
        assertEquals("https://archive.org/download/book7777/book.epub", picked[Formats.MIME_EPUB])
        assertEquals("https://archive.org/download/book7777/book.pdf", picked[Formats.MIME_PDF])
        assertEquals("https://archive.org/download/book7777/book_djvu.txt", picked[Formats.MIME_TEXT])
    }

    @Test
    fun `pdf turunan hanya dipakai bila tidak ada pdf asli`() {
        val onlyDerivative = listOf(file("book_text.pdf", "Text PDF"))
        val picked = ArchiveFormats.pick(onlyDerivative, "id1")
        assertEquals("https://archive.org/download/id1/book_text.pdf", picked[Formats.MIME_PDF])
    }

    @Test
    fun `tanpa kandidat readable hasilnya map kosong`() {
        val files = listOf(file("cover.jpg", "JPEG"))
        assertTrue(ArchiveFormats.pick(files, "id1").isEmpty())
    }

    @Test
    fun `url unduhan mengencode spasi tetapi mempertahankan slash segmen`() {
        assertEquals(
            "https://archive.org/download/id1/My%20Book.pdf",
            ArchiveFormats.downloadUrl("id1", "My Book.pdf"),
        )
        assertEquals(
            "https://archive.org/download/id1/dir/file.txt",
            ArchiveFormats.downloadUrl("id1", "dir/file.txt"),
        )
    }

    @Test
    fun `deteksi format berdasarkan nama file ketika label format hilang`() {
        val files = listOf(file("story.epub"), file("story_djvu.txt"))
        val picked = ArchiveFormats.pick(files, "x")
        assertEquals(2, picked.size)
        assertTrue(picked.containsKey(Formats.MIME_EPUB))
        assertTrue(picked.containsKey(Formats.MIME_TEXT))
    }
}
