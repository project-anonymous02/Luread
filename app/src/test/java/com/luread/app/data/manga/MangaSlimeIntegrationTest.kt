package com.luread.app.data.manga

import com.luread.app.data.network.ApiResult
import com.luread.app.data.network.HttpConfig
import com.luread.app.data.network.createHttpClient
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Uji integrasi JARINGAN — jalur sungguhan [MangaRepository.chapters]
 * terhadap manga "Tensei Shitara Slime datta Ken".
 *
 * LUREAD-FIX yang divalidasi (data MangaDex 2026-10-15):
 *  * chapter 83, 126–128, 131–136 hanya ada di bahasa lain (es-la/pt-br/ar)
 *    → sebelumnya hilang (feed id+en) sehingga nomor lompat;
 *  * chapter en 1, 2, 3, 138–140 adalah tautan eksternal KManga pages=0
 *    → sebelumnya tampil dan membaca "No pages in this chapter";
 *  * ch. 138 dobel (id + en) → sekarang satu entri dengan halaman.
 *
 * Lewat otomatis (Assume) bila MangaDex tidak terjangkau.
 */
class MangaSlimeIntegrationTest {

    private val client = createHttpClient(HttpConfig())
    private val repo = MangaRepository(MangaDexApi(client))

    private val mangaId = "e78a489b-6632-4d61-b00b-5206f5b8b22b"

    @After
    fun tearDown() {
        client.close()
    }

    private fun chaptersOrSkip(): List<MangaRepository.ChapterSummary> {
        var lastError: String? = null
        repeat(3) { attempt ->
            val result = runBlocking { repo.chapters(mangaId) }
            if (result is ApiResult.Success) return result.value
            lastError = (result as ApiResult.Failure).error.toString()
            if (attempt < 2) Thread.sleep(1_500)
        }
        assumeTrue("jaringan MangaDex tidak tersedia: $lastError", false)
        error("unreachable")
    }

    @Test
    fun `daftar terurut numerik naik tanpa nomor ganda`() {
        val chapters = chaptersOrSkip()
        assertTrue("daftar kosong", chapters.isNotEmpty())
        val numbers = chapters.mapNotNull { it.number?.toFloatOrNull() }
        assertEquals("tidak terurut numerik", numbers, numbers.sorted())
        val seen = mutableSetOf<String>()
        for (ch in chapters) {
            val key = ch.number ?: "id:${ch.id}"
            assertTrue("nomor ganda: ${ch.number}", seen.add(key))
        }
    }

    @Test
    fun `tidak ada chapter pages 0`() {
        val chapters = chaptersOrSkip()
        val zeros = chapters.filter { (it.pages ?: 0) == 0 }
        assertTrue("chapter 0 pp: ${zeros.map { it.number }}", zeros.isEmpty())
    }

    @Test
    fun `chapter yang hanya ada di bahasa lain tetap tampil`() {
        val chapters = chaptersOrSkip()
        val numbers = chapters.mapNotNull { it.number?.toFloatOrNull() }.toSet()
        for (n in listOf(131f, 132f, 133f, 134f, 135f, 136f)) {
            assertTrue("chapter $n hilang", n in numbers)
        }
    }

    @Test
    fun `nomor 138 satu entri terbaca bukan tautan eksternal`() {
        val chapters = chaptersOrSkip()
        val ch138 = chapters.filter { it.number == "138" }
        assertEquals("138 dobel/absen", 1, ch138.size)
        assertTrue("138 tetap 0 pp", (ch138[0].pages ?: 0) > 0)
    }

    @Test
    fun `nomor non-id-en terisi dan external-only tidak tampil`() {
        val chapters = chaptersOrSkip()
        val numbers = chapters.mapNotNull { it.number?.toFloatOrNull() }.toSet()
        // 83 hanya ada di es-la + punya halaman → sekarang tampil.
        assertTrue("83 (es-la) hilang", 83f in numbers)
        // 1, 2, 3 hanya tautan KManga pages=0 (tak ada versi gambar) → tidak
        // ditampilkan agar daftar tidak berisi entri yang error saat dibuka.
        for (n in listOf(1f, 2f, 3f)) {
            assertTrue("$n seharusnya tidak tampil", n !in numbers)
        }
    }
}
