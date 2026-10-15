package com.luread.app.data.komiku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Uji parser Komiku dengan potongan HTML yang strukturnya identik dengan
 * halaman langsung komiku.org / api.komiku.org (2026-10-15).
 * LUREAD-FEAT: selector ini adalah kontrak scraping sumber kedua.
 */
class KomikuParserTest {

    private val searchHtml = """
        <div class="bge">
            <div class="bgei">
                <a href="/manga/tensei-shitara-slime-datta-ken-indo/">
                    <img src="https://thumbnail.komiku.org/uploads/manga/tensei-shitara-slime-datta-ken-indo/manga_thumbnail-Manga-Tensei-Shitara-Slime-Datta-Ken.jpg?resize=450,235" class="lazy sd rd" alt="Tensei Shitara Slime Datta Ken">
                    <div class="tpe1_inf"><b>Manga</b> Isekai</div>
                </a>
            </div>
            <div class="kan">
                <a href="/manga/tensei-shitara-slime-datta-ken-indo/">
                    <h3>Tensei Shitara Slime Datta Ken</h3>
                </a>
                <p>Update 3 minggu lalu.</p>
                <div class="new1">
                    <a href="/tensei-shitara-slime-datta-ken-chapter-1/" title="Tensei Chapter 1">
                        <span>Awal: </span><span>Chapter 1</span>
                    </a>
                </div>
                <div class="new1">
                    <a href="/tensei-shitara-slime-datta-ken-chapter-146/" title="Tensei Chapter 146">
                        <span>Terbaru: </span><span>Chapter 146</span>
                    </a>
                </div>
            </div>
        </div>
        <div class="bge">
            <div class="bgei">
                <a href="/genre/action/">
                    <img src="/asset/img/jp.png">
                </a>
            </div>
            <div class="kan"><a href="/genre/action/"><h3>Bukan Manga</h3></a></div>
        </div>
    """.trimIndent()

    private val detailHtml = """
        <div id="Judul">
            <header>
                <h1><span>Komik <span itemprop="name">Tensei Shitara Slime Datta Ken</span></span></h1>
            </header>
        </div>
        <div id="Informasi">
            <div class="ims">
                <img src="https://thumbnail.komiku.to/uploads/manga/tensei-shitara-slime-datta-ken-indo/manga_thumbnail.jpg?w=500" itemprop="image">
            </div>
            <table>
                <tr><td>Alternative:</td><td>That Time I Got Reincarnated as a Slime</td></tr>
                <tr><td>Author:</td><td>Fuse</td></tr>
                <tr><td>Status:</td><td>Ongoing</td></tr>
            </table>
        </div>
        <ul class="genre">
            <li class="genre"><a href="/genre/fantasy/" title="Komik Genre Fantasy"><span>Fantasy</span></a></li>
            <li class="genre"><a href="/genre/isekai/" title="Komik Genre Isekai"><span>Isekai</span></a></li>
            <li class="genre"><a href="/genre/fantasy/" title="Komik Genre Fantasy"><span>Fantasy</span></a></li>
        </ul>
        <div id="Sinopsis" style="display:none">
            <div class="lsf"><div class="ls1"><img src="preview.jpg"></div></div>
        </div>
        <h2>Sinopsis Lengkap Komik Tensei Shitara Slime Datta Ken</h2>
        <p>Cerita ini mengikuti perjalanan seorang pria bernama Satoru Mikami yang meninggal dunia secara tragis dan terlahir kembali dalam dunia fantasi sebagai slime.</p>
        <p>Pilih chapter pada tabel dibawah untuk mulai membaca Komik.</p>
        <div style="max-height:500px;overflow-y:auto">
            <table id="Daftar_Chapter" itemscope itemtype="https://schema.org/ItemList">
                <meta itemprop="numberOfItems" content="148">
                <tr><th class="judulseries">Chapter</th><th class="tanggalseries">Tanggal Rilis</th></tr>
                <tr itemprop="itemListElement">
                    <td class="judulseries"><a href="/tensei-shitara-slime-datta-ken-chapter-146/" itemprop="url"><span itemprop="name"><b>Chapter 146</b></span></a></td>
                    <td class="tanggalseries">25/09/2026</td>
                </tr>
                <tr itemprop="itemListElement">
                    <td class="judulseries"><a href="/tensei-shitara-slime-datta-ken-chapter-145/"><span><b>Chapter 145</b></span></a></td>
                    <td class="tanggalseries">26/08/2026</td>
                </tr>
                <tr itemprop="itemListElement">
                    <td class="judulseries"><a href="/tensei-shitara-slime-datta-ken-chapter-145-b/"><span><b>Chapter 145</b></span></a></td>
                    <td class="tanggalseries">01/01/2026</td>
                </tr>
                <tr itemprop="itemListElement">
                    <td class="judulseries"><a href="/tensei-shitara-slime-datta-ken-chapter-10/"><span><b>Chapter 10</b></span></a></td>
                    <td class="tanggalseries">01/02/2025</td>
                </tr>
                <tr itemprop="itemListElement">
                    <td class="judulseries"><a href="/tensei-shitara-slime-datta-ken-chapter-01/"><span><b>Chapter 01</b></span></a></td>
                    <td class="tanggalseries">01/01/2025</td>
                </tr>
                <tr itemprop="itemListElement">
                    <td class="judulseries"><a href="/tensei-shitara-slime-datta-ken-chapter-10-5/"><span><b>Chapter 10.5</b></span></a></td>
                    <td class="tanggalseries">03/02/2025</td>
                </tr>
            </table>
        </div>
    """.trimIndent()

    private val chapterHtml = """
        <div id="Baca_Komik" onclick="klik('body')">
            <h2 class="judulbaca">Baca Online</h2>
            <div class="iklan mobile"></div>
            <img src="https://image2.komiku.to/komiku-promosi.webp">
            <img src="https://image2.komiku.to/upload5/tensei/146/1.webp" class="klazy ww" id="1">
            <img src="https://image3.komiku.to/upload5/tensei/146/2.webp" class="klazy ww" id="2">
            <img src="/asset/img/jp.png">
            <img src="https://www.gstatic.com/firebasejs/ui/2.0.0/images/auth/google.svg">
            <img src="https://image2.komiku.to/upload5/tensei/146/1.webp" class="klazy ww" id="1">
        </div>
    """.trimIndent()

    @Test
    fun `parseSearch mengekstrak kartu dan membuang non-manga`() {
        val items = KomikuParser.parseSearch(searchHtml)
        assertEquals(1, items.size)
        val item = items.single()
        assertEquals("tensei-shitara-slime-datta-ken-indo", item.slug)
        assertEquals("Tensei Shitara Slime Datta Ken", item.title)
        assertTrue(item.thumbUrl!!.startsWith("https://thumbnail.komiku.org/"))
        assertEquals("Manga", item.type)
        assertEquals("tensei-shitara-slime-datta-ken-chapter-1", item.firstChapter)
        assertEquals("tensei-shitara-slime-datta-ken-chapter-146", item.latestChapter)
    }

    @Test
    fun `parseSearch membuang duplikat slug`() {
        val items = KomikuParser.parseSearch(searchHtml + searchHtml)
        assertEquals(1, items.size)
    }

    @Test
    fun `slugFrom menerima relatif dan absolut`() {
        assertEquals("one-piece", KomikuParser.slugFrom("/manga/one-piece/"))
        assertEquals("one-piece", KomikuParser.slugFrom("https://komiku.org/manga/one-piece/"))
        assertNull(KomikuParser.slugFrom("/genre/action/"))
        assertNull(KomikuParser.slugFrom(null))
    }

    @Test
    fun `chapterPath memotong slash luar menolak jalur dalam`() {
        assertEquals(
            "tensei-shitara-slime-datta-ken-chapter-1",
            KomikuParser.chapterPath("/tensei-shitara-slime-datta-ken-chapter-1/"),
        )
        assertEquals(
            "tensei-shitara-slime-datta-ken-chapter-1",
            KomikuParser.chapterPath("https://komiku.org/tensei-shitara-slime-datta-ken-chapter-1/"),
        )
        assertNull(KomikuParser.chapterPath("/manga/some-slug/"))
        assertNull(KomikuParser.chapterPath("/genre/action/"))
        assertNull(KomikuParser.chapterPath("/dalam/slash/"))
    }

    @Test
    fun `parseChapterNumber menangani format bab`() {
        assertEquals("146", KomikuParser.parseChapterNumber("Chapter 146"))
        assertEquals("146", KomikuParser.parseChapterNumber("Baca Chapter 146 Bahasa Indonesia"))
        assertEquals("10.5", KomikuParser.parseChapterNumber("Chapter 10.5"))
        assertEquals("01", KomikuParser.parseChapterNumber("Chapter 01"))
        assertNull(KomikuParser.parseChapterNumber("Oneshot"))
        assertNull(KomikuParser.parseChapterNumber(null))
    }

    @Test
    fun `parseDetail metadata judul author status genre sinopsis`() {
        val d = KomikuParser.parseDetail(detailHtml, "tensei-shitara-slime-datta-ken-indo")
        assertEquals("Tensei Shitara Slime Datta Ken", d.title)
        assertTrue(d.thumbUrl!!.startsWith("https://thumbnail.komiku.to/"))
        assertEquals("Fuse", d.author)
        assertEquals("Ongoing", d.status)
        assertEquals(listOf("Fantasy", "Isekai"), d.genres)
        assertTrue(d.synopsis!!.startsWith("Cerita ini mengikuti"))
        assertEquals(148, d.totalChapters)
    }

    @Test
    fun `parseDetail chapter terurut naik dedup nomor 01 setara 1`() {
        val d = KomikuParser.parseDetail(detailHtml, "slug")
        // Tabel asli terbaru-dulu: 146, 145, 145(dup), 10, 01, 10.5
        assertEquals(listOf("01", "10", "10.5", "145", "146"), d.chapters.map { it.number })
        assertEquals(listOf("1", "10", "10.5", "145", "146"), d.chapters.map { it.number!!.toFloat().toString().removeSuffix(".0") })
        // Duplikat 145 mempertahankan unggahan terbaru (occurrence pertama).
        assertEquals(1, d.chapters.count { it.number == "145" })
        assertEquals("tensei-shitara-slime-datta-ken-chapter-145", d.chapters.first { it.number == "145" }.path)
        assertEquals("26/08/2026", d.chapters.first { it.number == "145" }.dateText)
        assertTrue(d.chapters.all { !it.path.contains('/') })
    }

    @Test
    fun `parseDetail tanpa meta numberOfItems hasilkan null`() {
        val html = detailHtml.replace("<meta itemprop=\"numberOfItems\" content=\"148\">", "")
        val d = KomikuParser.parseDetail(html, "slug")
        assertNull(d.totalChapters)
    }

    @Test
    fun `parseChapterImages hanya gambar konten iklan tersaring`() {
        val images = KomikuParser.parseChapterImages(chapterHtml)
        assertEquals(
            listOf(
                "https://image2.komiku.to/upload5/tensei/146/1.webp",
                "https://image3.komiku.to/upload5/tensei/146/2.webp",
            ),
            images,
        )
    }

    @Test
    fun `parseChapterImages kosong bila tanpa konten`() {
        assertTrue(KomikuParser.parseChapterImages("<div id=\"Baca_Komik\"><img src=\"/asset/img/jp.png\"></div>").isEmpty())
        assertTrue(KomikuParser.parseChapterImages("<html><body>tidak ada</body></html>").isEmpty())
    }
}
