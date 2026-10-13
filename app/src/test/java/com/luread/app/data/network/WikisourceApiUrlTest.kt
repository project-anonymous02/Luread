package com.luread.app.data.network

import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WikisourceApiUrlTest {

    private fun params(url: String): List<Pair<String, String>> =
        url.substringAfter('?').split('&').map { part ->
            val (key, value) = part.split('=', limit = 2)
            URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
        }

    @Test
    fun `pencarian memakai daftar pencarian namespace 0`() {
        val url = WikisourceApi.searchUrl("cerita rakyat", 0)
        val map = params(url).toMap()
        assertTrue(url.startsWith("https://id.wikisource.org/w/api.php"))
        assertEquals("query", map["action"])
        assertEquals("search", map["list"])
        assertEquals("cerita rakyat", map["srsearch"])
        assertEquals("0", map["srnamespace"])
        assertEquals("20", map["srlimit"])
        assertEquals("json", map["format"])
        assertEquals("2", map["formatversion"])
        assertTrue(!map.containsKey("sroffset"))
    }

    @Test
    fun `offset lanjutan dikirim hanya saat bukan halaman pertama`() {
        val map = params(WikisourceApi.searchUrl("x", 20)).toMap()
        assertEquals("20", map["sroffset"])
    }

    @Test
    fun `browse allpages memakai apcontinue untuk lanjutan`() {
        val fresh = params(WikisourceApi.browseUrl(null)).toMap()
        assertEquals("allpages", fresh["list"])
        assertEquals("20", fresh["aplimit"])
        assertTrue(!fresh.containsKey("apcontinue"))

        val next = params(WikisourceApi.browseUrl("005b_Batak, Angkola")).toMap()
        assertEquals("005b_Batak, Angkola", next["apcontinue"])
    }

    @Test
    fun `parse meminta teks html berdasarkan pageid`() {
        val map = params(WikisourceApi.parseUrl(33610)).toMap()
        assertEquals("parse", map["action"])
        assertEquals("33610", map["pageid"])
        assertEquals("text", map["prop"])
    }

    @Test
    fun `wrapDocument menghasilkan dokumen html utuh dengan judul tersandera`() {
        val doc = WikisourceApi.wrapDocument("A & B <tag>", "<p>Isi</p>")
        assertTrue(doc.startsWith("<!doctype html>"))
        assertTrue(doc.contains("<title>A &amp; B &lt;tag&gt;</title>"))
        assertTrue(doc.contains("<p>Isi</p>"))
        assertTrue(doc.contains("</body></html>"))
    }

    @Test
    fun `escapeHtml menetralkan karakter html`() {
        assertEquals("a &amp; b &lt;c&gt;", WikisourceApi.escapeHtml("a & b <c>"))
    }
}
