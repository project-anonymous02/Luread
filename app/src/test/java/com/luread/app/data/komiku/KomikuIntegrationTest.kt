package com.luread.app.data.komiku

import com.luread.app.data.network.ApiResult
import com.luread.app.data.network.HttpConfig
import com.luread.app.data.network.createHttpClient
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Uji integrasi JARINGAN — jalur sungguhan [KomikuApi] (scraping komiku.org).
 *
 * LUREAD-FEAT yang divalidasi:
 *  * pencarian menemukan slug manga (kasus "Tensei Slime");
 *  * detail memuat daftar chapter lengkap terurut tanpa nomor ganda;
 *  * chapter pertama benar-benar terbaca (pages nyata) — pelengkap sumber
 *    MangaDex untuk judul/kasar yang tidak lengkap di sana.
 *
 * Lewat otomatis (Assume) bila Komiku tidak terjangkau; retry anti-fluktuasi.
 */
class KomikuIntegrationTest {

    private val client = createHttpClient(HttpConfig())
    private val api = KomikuApi(client)

    private val slug = "tensei-shitara-slime-datta-ken-indo"

    @After
    fun tearDown() {
        client.close()
    }

    private fun <T> fetchOrSkip(block: () -> ApiResult<T>): T {
        var lastError: String? = null
        repeat(3) { attempt ->
            val result = block()
            if (result is ApiResult.Success) return result.value
            lastError = (result as ApiResult.Failure).error.toString()
            if (attempt < 2) Thread.sleep(1_500)
        }
        assumeTrue("jaringan Komiku tidak tersedia: $lastError", false)
        error("unreachable")
    }

    @Test
    fun `pencarian menemukan slug tensura`() {
        val results = fetchOrSkip { runBlocking { api.search("tensei slime") } }
        assertTrue("hasil kosong", results.isNotEmpty())
        assertTrue(
            "slug tensura tidak ada: ${results.map { it.slug }}",
            results.any { it.slug == slug },
        )
        val tensura = results.first { it.slug == slug }
        assertEquals("Tensei Shitara Slime Datta Ken", tensura.title)
        assertNotNull(tensura.thumbUrl)
    }

    @Test
    fun `detail lengkap terurut tanpa nomor ganda`() {
        val detail = fetchOrSkip { runBlocking { api.detail(slug) } }
        assertTrue("judul kosong", detail.title.isNotBlank())
        assertEquals("Fuse", detail.author)
        assertTrue("chapter sedikit: ${detail.chapters.size}", detail.chapters.size >= 100)

        val numbers = detail.chapters.mapNotNull { it.number?.toFloatOrNull() }
        assertEquals("tidak terurut", numbers, numbers.sorted())
        assertEquals("nomor ganda", numbers.size, numbers.toSet().size)
        // Komiku punya bab 1 (MangaDex hanya punya tautan external pages=0).
        assertTrue("bab 1 tidak ada", detail.chapters.any { it.number == "1" })
        // Tidak ada jalur bercabang yang memecah rute navigasi.
        assertTrue(detail.chapters.all { !it.path.contains('/') })
    }

    @Test
    fun `chapter pertama punya gambar terbaca`() {
        val detail = fetchOrSkip { runBlocking { api.detail(slug) } }
        val first = detail.chapters.first()
        val images = fetchOrSkip { runBlocking { api.pages(first.path) } }
        assertTrue("gambar sedikit: ${images.size}", images.size >= 3)
        assertTrue(images.all { it.startsWith("http") })
        // Iklan promosi tidak boleh ikut.
        assertTrue(images.none { it.contains("promosi") })
    }
}
