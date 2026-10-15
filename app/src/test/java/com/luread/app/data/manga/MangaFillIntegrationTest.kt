package com.luread.app.data.manga

import com.luread.app.data.komiku.KomikuApi
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
 * Uji integrasi JARINGAN — jalur penuh [MangaRepository.detail] dengan
 * sumber ganda MangaDex + Komiku (LUREAD-FEAT pengisian lubang).
 *
 * Divalidasi (data 2026-10-15):
 *  * SPY×FAMILY: MangaDex hanya 71 chapter terbaca (83 nomor hilang —
 *    tautan eksternal/lisensi) → setelah pengisian Komiku daftar penuh
 *    tanpa satu pun lubang di rentang nomor;
 *  * Tensei Shitara Slime datta Ken: nomor 1–3 (MangaDex hanya punya
 *    tautan KManga pages=0) kini tampil dari Komiku dan lubang tertutup.
 *
 * Lewat otomatis (Assume) bila MangaDex/Komiku tidak terjangkau; retry
 * anti-fluktuasi.
 */
class MangaFillIntegrationTest {

    private val client = createHttpClient(HttpConfig())
    private val komikuApi = KomikuApi(client)
    private val repo = MangaRepository(MangaDexApi(client), komikuApi)

    private val spyId = "6b958848-c885-4735-9201-12ee77abcb3c"
    private val slimeId = "e78a489b-6632-4d61-b00b-5206f5b8b22b"

    @After
    fun tearDown() {
        client.close()
    }

    /** Komiku sumber pengisian — bila mati, uji dilewati (bukan gagal). */
    private fun assumeKomikuAlive() {
        val probe = runBlocking { komikuApi.search("spy x family") }
        if (probe is ApiResult.Failure) {
            assumeTrue("jaringan Komiku tidak tersedia: ${probe.error}", false)
        }
    }

    private fun detailOrSkip(mangaId: String): MangaRepository.MangaSummary {
        var lastError: String? = null
        repeat(3) { attempt ->
            val result = runBlocking { repo.detail(mangaId) }
            if (result is ApiResult.Success) {
                cache[mangaId] = result.value.second
                return result.value.first
            }
            lastError = (result as ApiResult.Failure).error.toString()
            if (attempt < 2) Thread.sleep(1_500)
        }
        assumeTrue("jaringan MangaDex/Komiku tidak tersedia: $lastError", false)
        error("unreachable")
    }

    private val cache = mutableMapOf<String, List<MangaRepository.ChapterSummary>>()

    private fun chaptersOf(mangaId: String): List<MangaRepository.ChapterSummary> {
        detailOrSkip(mangaId)
        return cache.getValue(mangaId)
    }

    private fun gapsOf(chapters: List<MangaRepository.ChapterSummary>): List<Int> {
        val numbers = chapters.mapNotNull { it.number?.toFloatOrNull() }.toSet()
        if (numbers.isEmpty()) return emptyList()
        val lo = numbers.minOrNull()!!.toInt()
        val hi = numbers.maxOrNull()!!.toInt()
        return (lo..hi).filter { it.toFloat() !in numbers }
    }

    @Test
    fun `spy family — lubang mangaDex terisi komiku tanpa ganda dan terurut`() {
        assumeKomikuAlive()
        val chapters = chaptersOf(spyId)
        assertTrue("daftar kosong", chapters.isNotEmpty())

        val numbers = chapters.mapNotNull { it.number?.toFloatOrNull() }
        assertEquals("tidak terurut numerik", numbers, numbers.sorted())
        val seen = mutableSetOf<String>()
        for (ch in chapters) {
            assertTrue("nomor ganda: ${ch.number}", seen.add(ch.number ?: "id:${ch.id}"))
        }

        assertTrue("pengisian Komiku tidak berjalan", chapters.any { it.id.startsWith("komiku:") })
        assertEquals("lubang tersisa", emptyList<Int>(), gapsOf(chapters))
        val zeros = chapters.filter { it.pages == 0 }
        assertTrue("chapter 0 pp: ${zeros.map { it.number }}", zeros.isEmpty())
    }

    @Test
    fun `tensura — nomor 1-3 terisi komiku dan lubang tertutup`() {
        assumeKomikuAlive()
        val chapters = chaptersOf(slimeId)
        val numbers = chapters.mapNotNull { it.number?.toFloatOrNull() }.toSet()

        // Mdex hanya punya tautan KManga pages=0 untuk 1–3 → dari Komiku.
        for (n in listOf(1f, 2f, 3f)) {
            assertTrue("chapter $n tetap hilang", n in numbers)
        }
        assertTrue("pengisian Komiku tidak berjalan", chapters.any { it.id.startsWith("komiku:") })
        assertEquals("lubang tersisa", emptyList<Int>(), gapsOf(chapters))
        val zeros = chapters.filter { it.pages == 0 }
        assertTrue("chapter 0 pp: ${zeros.map { it.number }}", zeros.isEmpty())
    }
}
