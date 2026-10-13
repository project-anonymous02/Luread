package com.luread.app.data.network

import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveApiUrlTest {

    private fun params(url: String): List<Pair<String, String>> =
        url.substringAfter('?').split('&').map { part ->
            val (key, value) = part.split('=', limit = 2)
            URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
        }

    @Test
    fun `pencarian kosong memakai subject fiction dan urutkan unduhan`() {
        val url = ArchiveApi.searchUrl(null, emptyList(), 1)
        val map = params(url).toMap()
        assertTrue(url.startsWith("https://archive.org/advancedsearch.php"))
        assertEquals(ArchiveApi.BASE_QUERY + ArchiveApi.DEFAULT_TAIL, map["q"])
        assertEquals("downloads desc", map["sort[]"])
        assertEquals("20", map["rows"])
        assertEquals("1", map["page"])
        assertEquals("json", map["output"])
    }

    @Test
    fun `kata kunci dibungkus frasa dan bahasa diterjemahkan ke iso 639-2`() {
        val map = params(ArchiveApi.searchUrl("cerita rakyat", listOf("id"), 2)).toMap()
        assertEquals(ArchiveApi.BASE_QUERY + " AND language:ind AND title:(\"cerita rakyat\")", map["q"])
        assertEquals("2", map["page"])
        // Tidak ada sort saat pencarian: relevansi bawaan lebih relevan.
        assertTrue(!map.containsKey("sort[]"))
    }

    @Test
    fun `karakter khusus pada kata kunci di-escape agar query solr tetap utuh`() {
        val map = params(ArchiveApi.searchUrl("say \"hi\" (now)", listOf("en"), 1)).toMap()
        assertTrue(map["q"]!!.endsWith(" AND title:(\"say \\\"hi\\\" (now)\")"))
        assertTrue(map["q"]!!.contains("language:eng"))
    }

    @Test
    fun `field fl terkirim untuk semua kolom yang dibutuhkan mapper`() {
        val pairs = params(ArchiveApi.searchUrl("x", emptyList(), 1))
        val fields = pairs.filter { it.first == "fl[]" }.map { it.second }
        assertEquals(ArchiveApi.SEARCH_FIELDS, fields)
    }

    @Test
    fun `url metadata internet archive terbentuk dari identifier`() {
        assertEquals(
            "https://archive.org/metadata/prideprejudice01aust",
            ArchiveApi.metadataUrl("prideprejudice01aust"),
        )
    }

    @Test
    fun `mapper dokumen menghasilkan kunci dan cover layanan archive`() {
        val doc = ArchiveDoc(
            identifier = "cerita-rakyat",
            title = kotlinx.serialization.json.JsonPrimitive("Cerita Rakyat"),
            creator = kotlinx.serialization.json.JsonArray(
                listOf(kotlinx.serialization.json.JsonPrimitive("Budi")),
            ),
            downloads = 42,
        )
        val item = doc.toItem()
        assertEquals("archive:cerita-rakyat", item.key)
        assertEquals("Cerita Rakyat", item.title)
        assertEquals(listOf("Budi"), item.authors)
        assertEquals("https://archive.org/services/img/cerita-rakyat", item.coverUrl)
        assertEquals(42, item.downloadCount)
        assertEquals("https://archive.org/details/cerita-rakyat", item.canonicalUrl)
    }

    @Test
    fun `field bertipe string atau array dinormalisasi`() {
        assertEquals("Satu", kotlinx.serialization.json.JsonPrimitive("Satu").elementText())
        val list = kotlinx.serialization.json.buildJsonArray {
            add(kotlinx.serialization.json.JsonPrimitive("A"))
            add(kotlinx.serialization.json.JsonPrimitive("B"))
        }
        assertEquals("A", list.elementText())
        assertEquals(listOf("A", "B"), list.elementTextList())
        assertFalse(kotlinx.serialization.json.JsonNull.elementTextList().isNotEmpty())
    }
}
