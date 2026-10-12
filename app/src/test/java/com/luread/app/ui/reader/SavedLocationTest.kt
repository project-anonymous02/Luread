package com.luread.app.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedLocationTest {

    @Test
    fun `round trip menyimpan href progression dan page`() {
        val original = SavedLocation(
            href = "chapter2.xhtml",
            progression = 0.42,
            text = "12",
            page = 7,
        )
        val encoded = LocationCodec.encode(original)
        val decoded = LocationCodec.decode(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `field yang dihilangkan tidak membuat JSON rusak`() {
        val encoded = LocationCodec.encode(SavedLocation(href = "a.xhtml"))
        val decoded = LocationCodec.decode(encoded)
        assertEquals(SavedLocation(href = "a.xhtml"), decoded)
        assertNull(decoded?.progression)
        assertNull(decoded?.page)
    }

    @Test
    fun `field tambahan diabaikan`() {
        val decoded = LocationCodec.decode("""{"href":"x","unknown":true}""")
        assertEquals(SavedLocation(href = "x"), decoded)
    }

    @Test
    fun `json rusak atau kosong mengembalikan null`() {
        assertNull(LocationCodec.decode(null))
        assertNull(LocationCodec.decode(""))
        assertNull(LocationCodec.decode("{not json"))
        assertNull(LocationCodec.decode("[]"))
    }

    @Test
    fun `progression di luar rentang tetap tersimpan apa adanya`() {
        val decoded = LocationCodec.decode("""{"href":"x","progression":0.5}""")
        assertEquals(0.5, decoded?.progression!!, 0.0)
    }
}
