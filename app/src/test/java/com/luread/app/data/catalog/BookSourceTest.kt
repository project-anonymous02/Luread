package com.luread.app.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookSourceTest {

    @Test
    fun `kunci komposit memakai prefix sumber yang benar`() {
        assertEquals("gutenberg:1342", BookSource.GUTENDEX.prefix + "1342")
        assertEquals("openlibrary:OL66554W", BookSource.OPEN_LIBRARY.prefix + "OL66554W")
        assertEquals("archive:prideprejudice01aust", BookSource.INTERNET_ARCHIVE.prefix + "prideprejudice01aust")
        assertEquals("wikisource:33610", BookSource.WIKISOURCE.prefix + "33610")
        assertEquals("wikibooks:24258", BookSource.WIKIBOOKS.prefix + "24258")
    }

    @Test
    fun `fromKey membaca sumber dari kunci dan jatuh ke gutenberg untuk kunci asing`() {
        assertEquals(BookSource.OPEN_LIBRARY, BookSource.fromKey("openlibrary:OL66554W"))
        assertEquals(BookSource.INTERNET_ARCHIVE, BookSource.fromKey("archive:abcdef"))
        assertEquals(BookSource.WIKISOURCE, BookSource.fromKey("wikisource:33610"))
        assertEquals(BookSource.WIKIBOOKS, BookSource.fromKey("wikibooks:24258"))
        assertEquals(BookSource.GUTENDEX, BookSource.fromKey("gutenberg:1342"))
        // Impor lokal tidak dikenali sebagai sumber katalog mana pun.
        assertEquals(BookSource.GUTENDEX, BookSource.fromKey("local:1700000000000"))
    }

    @Test
    fun `remoteIdOf mengembalikan id sumber atau null untuk prefix lain`() {
        assertEquals("OL66554W", BookSource.OPEN_LIBRARY.remoteIdOf("openlibrary:OL66554W"))
        assertNull(BookSource.OPEN_LIBRARY.remoteIdOf("gutenberg:1342"))
        assertNull(BookSource.GUTENDEX.remoteIdOf("local:1"))
        assertNull(BookSource.GUTENDEX.remoteIdOf("gutenberg:"))
    }

    @Test
    fun `tidak ada prefix yang mengandung slash agar aman jadi nama file`() {
        BookSource.entries.forEach { source ->
            assertTrue(!source.id.contains('/'))
        }
    }
}
