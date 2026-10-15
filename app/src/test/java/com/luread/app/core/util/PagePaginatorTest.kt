package com.luread.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Uji pembagian halaman visual — inti pembaca horizontal (LUREAD-FIX:
 * tidak ada gulir vertikal, jadi pemotongan teks harus tepat).
 */
class PagePaginatorTest {

    @Test
    fun `linesPerPage membagi tinggi area dengan tinggi baris`() {
        assertEquals(20, PagePaginator.linesPerPage(1000f, 50f))
        assertEquals(13, PagePaginator.linesPerPage(1000f, 75f)) // floor(13.33)
        assertEquals(2, PagePaginator.linesPerPage(100f, 50f))
    }

    @Test
    fun `linesPerPage menolak nilai tidak valid dan selalu minimal 1`() {
        assertEquals(1, PagePaginator.linesPerPage(0f, 50f))
        assertEquals(1, PagePaginator.linesPerPage(-100f, 50f))
        assertEquals(1, PagePaginator.linesPerPage(1000f, 0f))
        assertEquals(1, PagePaginator.linesPerPage(Float.NaN, 50f))
        assertEquals(1, PagePaginator.linesPerPage(Float.POSITIVE_INFINITY, 50f))
    }

    @Test
    fun `lineRanges membelah tepat dengan sisa di halaman terakhir`() {
        assertEquals(listOf(0..9, 10..19, 20..24), PagePaginator.lineRanges(25, 10))
        assertEquals(listOf(0..9, 10..19), PagePaginator.lineRanges(20, 10))
        assertEquals(listOf(0..4), PagePaginator.lineRanges(5, 10))
    }

    @Test
    fun `lineRanges mengembalikan kosong untuk nol baris dan aman terhadap nilai negatif`() {
        assertTrue(PagePaginator.lineRanges(0, 10).isEmpty())
        assertTrue(PagePaginator.lineRanges(-3, 10).isEmpty())
        // linesPerPage <= 0 dipaksa menjadi 1 agar tidak loop tanpa akhir.
        assertEquals(listOf(0..0, 1..1), PagePaginator.lineRanges(2, 0))
    }

    @Test
    fun `paginate memotong teks per baris menggunakan lineEnd`() {
        val text = "aaa\nbbb\nccc\nddd"
        // Layout fiktif: tiap baris 4 karakter termasuk newline, baris terakhir tanpa \n.
        val lineEnd = { line: Int -> ((line + 1) * 4).coerceAtMost(text.length) }
        val pages = PagePaginator.paginate(text, totalLines = 4, linesPerPage = 2, lineEnd = lineEnd)
        assertEquals(listOf("aaa\nbbb\n", "ccc\nddd"), pages)
    }

    @Test
    fun `paginate mengembalikan seluruh teks bila linesPerPage kecil atau tak ada baris`() {
        val text = "satu dua tiga"
        assertEquals(listOf(text), PagePaginator.paginate(text, totalLines = 10, linesPerPage = 1) { 0 })
        assertEquals(listOf(text), PagePaginator.paginate(text, totalLines = 0, linesPerPage = 5) { 0 })
        assertEquals(listOf(""), PagePaginator.paginate("", totalLines = 0, linesPerPage = 5) { 0 })
    }

    @Test
    fun `paginate tidak pernah membuang atau menduplikasi teks`() {
        val text = (1..50).joinToString(" ") { "kata$it" }
        val pages = PagePaginator.paginate(text, totalLines = 50, linesPerPage = 10) { line ->
            // lineEnd kasar: asumsi tiap baris = 10 kata pertama.
            text.length.coerceAtMost((line + 1) * text.length / 50)
        }
        assertEquals(text, pages.joinToString(""))
        assertTrue(pages.size >= 2)
    }

    @Test
    fun `paginateByChars membagi per karakter tanpa memotong kata bila ada spasi`() {
        val text = "kata pertama kata kedua kata ketiga"
        val pages = PagePaginator.paginateByChars(text, charsPerPage = 16)
        assertTrue(pages.size > 1)
        for (page in pages) {
            assertTrue("halaman kosong terdeteksi", page.isNotBlank())
        }
        assertEquals(text, pages.joinToString(""))
        // Batas potongan jatuh di spasi, bukan di tengah kata.
        for (page in pages.dropLast(1)) {
            assertTrue(page.endsWith(" "))
        }
    }

    @Test
    fun `paginateByChars mengembalikan satu halaman untuk teks pendek`() {
        assertEquals(
            listOf("pendek"),
            PagePaginator.paginateByChars("pendek", charsPerPage = 100),
        )
        assertEquals(
            listOf(""),
            PagePaginator.paginateByChars("", charsPerPage = 100),
        )
        // charsPerPage tidak valid dipaksa minimal 1 (tidak loop tanpa akhir).
        val pages = PagePaginator.paginateByChars("abc", charsPerPage = 0)
        assertEquals(listOf("a", "b", "c"), pages)
    }
}
