package com.luread.app.data.catalog

import com.luread.app.data.network.GutendexBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogItemTest {

    @Test
    fun `fromGutendex memindahkan semua field termasuk cover dari formats`() {
        val book = GutendexBook(
            id = 1342,
            title = "Pride and Prejudice",
            authors = listOf(com.luread.app.data.network.GutendexPerson(name = "Jane Austen")),
            summaries = listOf("Summary text"),
            subjects = listOf("Friendship"),
            languages = listOf("en"),
            copyright = false,
            formats = mapOf(
                Formats.MIME_EPUB to "https://example.org/1342.epub",
                Formats.MIME_JPEG to "https://example.org/1342.jpg",
            ),
            downloadCount = 123,
        )
        val item = CatalogItem.fromGutendex(book)
        assertEquals("gutenberg:1342", item.key)
        assertEquals("Pride and Prejudice", item.title)
        assertEquals(listOf("Jane Austen"), item.authors)
        assertEquals("Summary text", item.summary)
        assertEquals(listOf("Friendship"), item.subjects)
        assertEquals(listOf("en"), item.languages)
        assertEquals("https://example.org/1342.jpg", item.coverUrl)
        assertEquals(123, item.downloadCount)
        assertEquals(false, item.copyright)
        assertEquals(BookSource.GUTENDEX, item.source)
        assertEquals(book.formats, item.formats)
        assertNull(item.canonicalUrl)
    }

    @Test
    fun `coverUrl bisa dari field langsung ketika formats tidak punya jpeg`() {
        val item = CatalogItem(
            key = "archive:x",
            title = "T",
            authors = emptyList(),
            summary = null,
            subjects = emptyList(),
            languages = emptyList(),
            coverUrl = "https://archive.org/services/img/x",
            downloadCount = null,
            copyright = null,
            source = BookSource.INTERNET_ARCHIVE,
        )
        assertEquals("https://archive.org/services/img/x", item.coverUrl)
    }
}
