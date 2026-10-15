package com.luread.app.data.komiku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Uji kandidat kata kunci pencarian Komiku (LUREAD-FEAT): judul tanpa
 * spasi di sekitar × harus menghasilkan varian "SPY FAMILY" — query
 * "SPY×FAMILY" mentah mengembalikan 0 hasil di komiku.org.
 */
class KomikuSearchQueriesTest {

    @Test
    fun `judul dengan × menghasilkan varian berspasi yang ditemukan komiku`() {
        val queries = KomikuApi.searchQueries("SPY×FAMILY")
        assertEquals("SPY×FAMILY", queries.first())
        assertTrue("varian berspasi hilang: $queries", queries.any { it.contains(" ") && !it.contains("×") })
    }

    @Test
    fun `kandidat unik tanpa kosong dan pertama adalah judul asli`() {
        val queries = KomikuApi.searchQueries("  Tensei Slime  ")
        assertEquals(listOf("Tensei Slime"), queries)
    }

    @Test
    fun `judul yang sudah berspasi tidak menghasilkan duplikat`() {
        val queries = KomikuApi.searchQueries("Spy X Family")
        assertEquals(listOf("Spy X Family"), queries)
    }

    @Test
    fun `judul berisi simbol lain tetap menghasilkan kandidat bermakna`() {
        val queries = KomikuApi.searchQueries("Tomodachi Game!")
        assertTrue(queries.any { it.contains("Tomodachi") })
        assertTrue(queries.none { it.isBlank() })
    }
}
