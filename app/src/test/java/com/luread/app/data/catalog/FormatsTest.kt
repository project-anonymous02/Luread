package com.luread.app.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pengujian logika pemilihan format terhadap struktur JSON asli Gutendex.
 *
 * Data contoh diambil dari rekaman respons 12 Okt 2026 (docs/gutendex-api-audit.md):
 * key MIME membawa parameter charset, URL tidak seragam, dan tidak semua buku
 * memiliki EPUB.
 */
class FormatsTest {

    @Test
    fun `normalizeMime membuang parameter charset dan menyeragamkan huruf`() {
        assertEquals("text/plain", Formats.normalizeMime("text/plain; charset=utf-8"))
        assertEquals("text/plain", Formats.normalizeMime("text/plain; charset=us-ascii"))
        assertEquals("text/html", Formats.normalizeMime("TEXT/HTML"))
        assertEquals("application/epub+zip", Formats.normalizeMime(" application/epub+zip "))
    }

    @Test
    fun `urlsByMime memilih charset utf-8 saat dua varian muncul`() {
        val formats = mapOf(
            "text/plain; charset=us-ascii" to "https://example.org/a.txt",
            "text/plain; charset=utf-8" to "https://example.org/b.txt",
            "text/html; charset=utf-8" to "https://example.org/c.html",
        )
        val result = Formats.urlsByMime(formats)
        assertEquals(2, result.size)
        assertEquals("https://example.org/b.txt", result["text/plain"])
        assertEquals("https://example.org/c.html", result["text/html"])
    }

    @Test
    fun `urlsByMime mengabaikan nilai null dan kosong`() {
        val formats = mapOf(
            "application/epub+zip" to null,
            "text/html" to "",
            "image/jpeg" to "https://example.org/cover.jpg",
        )
        val result = Formats.urlsByMime(formats)
        assertEquals(1, result.size)
        assertNotNull(result["image/jpeg"])
    }

    @Test
    fun `bestReadable memprioritaskan EPUB lalu HTML lalu teks lalu PDF`() {
        val all = mapOf(
            "application/pdf" to "https://example.org/a.pdf",
            "text/plain" to "https://example.org/a.txt",
            "text/html" to "https://example.org/a.html",
            "application/epub+zip" to "https://example.org/a.epub",
        )
        assertEquals(Formats.Readable.EPUB, Formats.bestReadable(all))
        assertEquals(Formats.Readable.HTML, Formats.bestReadable(all - "application/epub+zip"))
        assertEquals(Formats.Readable.TEXT, Formats.bestReadable(all - "application/epub+zip" - "text/html"))
        assertEquals(Formats.Readable.PDF, Formats.bestReadable(all - "application/epub+zip" - "text/html" - "text/plain"))
    }

    @Test
    fun `bestReadable mengembalikan null bila tidak ada format terbaca`() {
        assertNull(Formats.bestReadable(emptyMap()))
        assertNull(
            Formats.bestReadable(
                mapOf("application/rdf+xml" to "https://example.org/1342.rdf"),
            ),
        )
    }

    @Test
    fun `coverUrl mengambil url jpeg`() {
        val formats = mapOf(
            "image/jpeg" to "https://example.org/cover.jpg",
            "text/html" to "https://example.org/a.html",
        )
        assertEquals("https://example.org/cover.jpg", Formats.coverUrl(formats))
        assertNull(Formats.coverUrl(mapOf("text/html" to "https://example.org/a.html")))
    }

    @Test
    fun `available mengurutkan format terbaca lebih dulu`() {
        val formats = mapOf(
            "application/rdf+xml" to "https://example.org/metadata.rdf",
            "image/jpeg" to "https://example.org/cover.jpg",
            "application/epub+zip" to "https://example.org/a.epub",
        )
        val available = Formats.available(formats)
        assertEquals(3, available.size)
        assertTrue(available.first().readable)
        assertTrue(available.none { it.url.isBlank() })
    }

    @Test
    fun `extensionFor memetakan mime ke ekstensi`() {
        assertEquals("epub", Formats.extensionFor(Formats.EPUB))
        assertEquals("pdf", Formats.extensionFor(Formats.PDF))
        assertEquals("txt", Formats.extensionFor(Formats.TEXT))
        assertEquals("html", Formats.extensionFor(Formats.HTML))
        assertEquals("bin", Formats.extensionFor("application/x-unknown"))
    }

    @Test
    fun `sniff mengenali magic number zip dan pdf`() {
        val zip = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00)
        val pdf = "%PDF-1.7".toByteArray()
        val html = "<html><body".toByteArray()

        assertEquals(Formats.EPUB_OR_ZIP, Formats.sniff(zip))
        assertEquals(Formats.PDF, Formats.sniff(pdf))
        assertNull(Formats.sniff(html))
        assertNull(Formats.sniff(ByteArray(0)))
    }

    @Test
    fun `looksLikeEpub hanya untuk zip`() {
        assertTrue(Formats.looksLikeEpub(byteArrayOf(0x50, 0x4B, 0x05, 0x06, 0, 0, 0, 0)))
        assertFalse(Formats.looksLikeEpub("%PDF-1.4".toByteArray()))
    }
}
