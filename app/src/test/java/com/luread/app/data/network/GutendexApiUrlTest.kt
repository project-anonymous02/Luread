package com.luread.app.data.network

import com.luread.app.data.catalog.CatalogQuery
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pengujian pembangunan URL katalog tanpa menyentuh jaringan.
 *
 * Bentuk URL yang dihasilkan dibandingkan dengan perilaku API yang sudah
 * diverifikasi (docs/gutendex-api-audit.md): path harus diakhiri slash
 * karena `/books` membalas 301, dan parameter yang tidak dipakai tidak boleh
 * ikut dikirim.
 */
class GutendexApiUrlTest {

    private val client = createHttpClient(HttpConfig())
    private val api = GutendexApi(client)

    @After
    fun tearDown() {
        client.close()
    }

    @Test
    fun `query kosong menghasilkan path katalog dengan slash`() {
        assertEquals("https://gutendex.com/books/", api.buildUrl(CatalogQuery()))
    }

    @Test
    fun `search languages topic mime ids dan page ikut tersusun`() {
        val url = api.buildUrl(
            CatalogQuery(
                search = "sherlock",
                languages = listOf("en"),
                topic = "children",
                mimeType = "application/epub+zip",
                ids = 1342,
                page = 2,
            ),
        )

        assertTrue(url.startsWith("https://gutendex.com/books/?"))
        assertTrue(url.contains("search=sherlock"))
        assertTrue(url.contains("languages=en"))
        assertTrue(url.contains("topic=children"))
        assertTrue(url.contains("mime_type=application"))
        assertTrue(url.contains("ids=1342"))
        assertTrue(url.contains("page=2"))
    }

    @Test
    fun `halaman pertama tidak mengirim parameter page`() {
        val url = api.buildUrl(CatalogQuery(page = 1, search = "alice"))
        assertFalse(url.contains("page="))
        assertTrue(url.contains("search=alice"))
    }

    @Test
    fun `parameter kosong tidak ikut dikirim`() {
        val url = api.buildUrl(
            CatalogQuery(search = "   ", topic = "", languages = emptyList()),
        )
        assertEquals("https://gutendex.com/books/", url)
    }

    @Test
    fun `beberapa bahasa menjadi parameter berulang`() {
        val url = api.buildUrl(CatalogQuery(languages = listOf("en", "fr")))
        assertTrue(url.contains("languages=en"))
        assertTrue(url.contains("languages=fr"))
    }

    @Test
    fun `hanya url katalog yang dipercaya`() {
        assertTrue(GutendexApi.isTrustedCatalogUrl("https://gutendex.com/books/?page=2"))
        assertFalse(GutendexApi.isTrustedCatalogUrl("https://example.com/books/?page=2"))
        assertFalse(GutendexApi.isTrustedCatalogUrl("http://gutendex.com/books/"))
        assertFalse(GutendexApi.isTrustedCatalogUrl("https://gutendex.com.evil.test/books/"))
        assertFalse(GutendexApi.isTrustedCatalogUrl("file:///etc/passwd"))
    }
}
