package com.luread.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtDocumentTest {

    @Test
    fun `teks pendek menjadi satu halaman`() {
        val pages = TxtDocument.paginate("Halo dunia")
        assertEquals(1, pages.size)
        assertEquals("Halo dunia", pages[0])
    }

    @Test
    fun `teks kosong tetap menghasilkan halaman`() {
        val pages = TxtDocument.paginate("   ")
        assertEquals(1, pages.size)
    }

    @Test
    fun `pemotongan menghindari memotong kata bila ada akhir baris`() {
        val line = "x".repeat(50)
        val text = buildString {
            repeat(200) { append(line).append('\n') }
        }
        val pages = TxtDocument.paginate(text, pageSize = 1_000)

        assertTrue(pages.size > 1)
        pages.forEach { page ->
            assertTrue(page.length <= 1_001)
            // Tidak ada potongan di tengah baris selama ada akhir baris yang cukup.
            if (page.length == 1_001) {
                assertTrue(page.endsWith("\n"))
            }
        }
        assertEquals(text, pages.joinToString(""))
    }

    @Test
    fun `tanpa akhir baris yang cukup potongan keras tetap terjadi`() {
        val text = "a".repeat(2_500)
        val pages = TxtDocument.paginate(text, pageSize = 1_000)
        assertEquals(3, pages.size)
        assertEquals(text, pages.joinToString(""))
    }

    @Test
    fun `crlf dinormalkan menjadi lf`() {
        val pages = TxtDocument.paginate("baris satu\r\nbaris dua")
        assertEquals(1, pages.size)
        assertTrue(pages[0].contains("baris satu\nbaris dua"))
        assertTrue(!pages[0].contains("\r"))
    }
}
