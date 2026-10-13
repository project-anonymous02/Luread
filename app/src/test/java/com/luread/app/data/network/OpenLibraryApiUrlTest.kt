package com.luread.app.data.network

import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenLibraryApiUrlTest {

    private fun params(url: String): List<Pair<String, String>> =
        url.substringAfter('?').split('&').map { part ->
            val (key, value) = part.split('=', limit = 2)
            URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
        }

    @Test
    fun `pencarian kosong memakai browse subject fiction dan sort editions`() {
        val url = OpenLibraryApi.searchUrl(null, emptyList(), 1)
        val map = params(url).toMap()
        assertTrue(url.startsWith("https://openlibrary.org/search.json"))
        assertEquals(OpenLibraryApi.DEFAULT_QUERY, map["q"])
        assertEquals("editions", map["sort"])
        assertEquals("20", map["limit"])
        assertEquals("1", map["page"])
        assertEquals(OpenLibraryApi.SEARCH_FIELDS.joinToString(","), map["fields"])
    }

    @Test
    fun `kata kunci dan bahasa diterjemahkan ke parameter yang teruji`() {
        val map = params(OpenLibraryApi.searchUrl("cerita rakyat", listOf("id"), 3)).toMap()
        assertEquals("cerita rakyat", map["q"])
        assertEquals("ind", map["language"])
        assertEquals("3", map["page"])
    }

    @Test
    fun `bahasa yang tidak dikenal diabaikan daripada mengirim kode salah`() {
        val url = OpenLibraryApi.searchUrl("x", listOf("xx"), 1)
        assertTrue(!url.contains("language="))
    }

    @Test
    fun `url karya edisi dan penulis terbentuk dari id bersih`() {
        assertEquals(
            "https://openlibrary.org/works/OL66554W.json",
            OpenLibraryApi.workUrl("OL66554W"),
        )
        assertEquals(
            "https://openlibrary.org/works/OL66554W/editions.json?limit=10",
            OpenLibraryApi.editionsUrl("OL66554W"),
        )
        assertEquals("https://openlibrary.org/authors/OL26275A.json", OpenLibraryApi.authorUrl("OL26275A"))
    }

    @Test
    fun `mapper dokumen search menghasilkan kunci tanpa slash`() {
        val doc = OpenLibraryDoc(
            key = "/works/OL66554W",
            title = "Pride and Prejudice",
            authorNames = listOf("Jane Austen"),
            coverId = 8231856,
        )
        val item = doc.toItem()
        assertEquals("openlibrary:OL66554W", item.key)
        assertEquals("https://covers.openlibrary.org/b/id/8231856-M.jpg", item.coverUrl)
        assertEquals("https://openlibrary.org/works/OL66554W", item.canonicalUrl)
        assertEquals(listOf("Jane Austen"), item.authors)
        assertEquals(com.luread.app.data.catalog.BookSource.OPEN_LIBRARY, item.source)
    }

    @Test
    fun `deskripsi karya mendukung string dan objek`() {
        val asString = OpenLibraryWork(description = kotlinx.serialization.json.JsonPrimitive("Hello"))
        assertEquals("Hello", asString.descriptionText)
        val asObject = OpenLibraryWork(
            description = kotlinx.serialization.json.buildJsonObject {
                put("type", kotlinx.serialization.json.JsonPrimitive("text"))
                put("value", kotlinx.serialization.json.JsonPrimitive("Dari objek"))
            },
        )
        assertEquals("Dari objek", asObject.descriptionText)
        assertEquals(null, OpenLibraryWork(description = null).descriptionText)
    }
}
