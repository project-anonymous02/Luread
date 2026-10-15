package com.luread.app.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Uji URL pencarian Internet Archive untuk baris Komik Indonesia (LUREAD-FEAT:
 * `subject:"komik indonesia"` + `language:ind`, diverifikasi 200 di audit).
 */
class ArchiveKomikUrlTest {

    @Test
    fun `searchUrl menyusun subjek komik indonesia dan bahasa ind`() {
        val url = ArchiveApi.searchUrl(
            search = null,
            languages = listOf("id"),
            page = 1,
            topic = "komik indonesia",
        )
        assertTrue(url.startsWith("https://archive.org/advancedsearch.php"))
        assertTrue(url.contains("subject%3A%22komik+indonesia%22"))
        assertTrue(url.contains("language%3Aind"))
        assertTrue(url.contains("output=json"))
        assertTrue(url.contains("rows=${ArchiveApi.PAGE_SIZE}"))
    }

    @Test
    fun `searchUrl dengan teks menambah klausa title dan tanpa subjek`() {
        val url = ArchiveApi.searchUrl(
            search = "naruto",
            languages = emptyList(),
            page = 2,
            topic = null,
        )
        assertTrue(url.contains("title%3A%28%22naruto%22%29"))
        assertTrue(url.contains("page=2"))
        assertFalse(url.contains("subject%3A"))
        // Kueri kosong tanpa topik memakai ekor default subject:fiction.
        val fallback = ArchiveApi.searchUrl(null, emptyList(), 1, null)
        assertTrue(fallback.contains("subject%3Afiction"))
    }

    @Test
    fun `escapePhrase mengamankan tanda kutip dan backslash SOLR`() {
        assertEquals("\"a\\\"b\"", ArchiveApi.escapePhrase("a\"b"))
        assertEquals("\"c\\\\d\"", ArchiveApi.escapePhrase("c\\d"))
        assertEquals("\"plain\"", ArchiveApi.escapePhrase("plain"))
    }

    @Test
    fun `BASE_QUERY memuat format yang bisa dibaca Luread`() {
        assertTrue(ArchiveApi.BASE_QUERY.contains("mediatype:texts"))
        assertTrue(ArchiveApi.BASE_QUERY.contains("EPUB"))
        assertTrue(ArchiveApi.BASE_QUERY.contains("DjVuTXT"))
        assertTrue(ArchiveApi.BASE_QUERY.contains("Comic Book ZIP"))
    }
}
