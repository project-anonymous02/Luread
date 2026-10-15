package com.luread.app.data.komiku

import org.jsoup.Jsoup

/**
 * Parser HTML Komiku (LUREAD-FEAT) — pola scraping dipelajari dari
 * `github.com/febryardiansyah/manga-api`, selector diverifikasi terhadap
 * HTML langsung pada 2026-10-15:
 *  * cari    → `https://api.komiku.org/?post_type=manga&s=…` (kartu `div.bge`)
 *  * detail  → `https://komiku.org/manga/<slug>/` (`#Daftar_Chapter`, 150 baris
 *              dalam satu halaman — tanpa paginasi)
 *  * gambar  → `https://komiku.org/<chapter>/` (`#Baca_Komik img.klazy`)
 *
 * Semua fungsi murni (String masuk, model keluar) agar bisa diuji offline
 * dengan potongan HTML asli.
 */
object KomikuParser {

    data class SearchItem(
        val slug: String,
        val title: String,
        val thumbUrl: String?,
        val type: String?,
        val firstChapter: String?,
        val latestChapter: String?,
    )

    /**
     * @param path jalur chapter TANPA slash (mis. `tensei-…-chapter-146`) —
     *   id aman untuk rute navigasi; jalur ber-slash dalam ditolak parser.
     * @param number nomor bab hasil ekstraksi ("146", "146.5", "01", null).
     */
    data class ChapterItem(
        val path: String,
        val number: String?,
        val title: String?,
        val dateText: String?,
    )

    data class Detail(
        val title: String,
        val thumbUrl: String?,
        val author: String?,
        val status: String?,
        val genres: List<String>,
        val synopsis: String?,
        /** Terurut nomor numerik naik (null di akhir), duplikat nomor dibuang. */
        val chapters: List<ChapterItem>,
        val totalChapters: Int?,
    )

    /** Jalur `/manga/<slug>/` (relatif/absolut) → slug; selain itu null. */
    fun slugFrom(href: String?): String? {
        if (href.isNullOrBlank()) return null
        val path = if (href.startsWith("http://") || href.startsWith("https://")) {
            href.substringAfter("://").substringAfter('/', "")
        } else href
        val trimmed = path.trimStart('/')
        if (!trimmed.startsWith("manga/")) return null
        val slug = trimmed.removePrefix("manga/").trim('/')
        if (slug.isEmpty() || slug.contains('/')) return null
        return slug
    }

    /**
     * Jalur chapter → id tanpa slash. Tolak jalur ber-slash dalam agar tidak
     * memecah rute navigasi `manga/read/{mangaId}/{chapterId}`.
     */
    fun chapterPath(href: String?): String? {
        if (href.isNullOrBlank()) return null
        val path = if (href.startsWith("http://") || href.startsWith("https://")) {
            href.substringAfter("://").substringAfter('/', "")
        } else href
        val trimmed = path.trim('/')
        if (trimmed.isEmpty() || trimmed.contains('/')) return null
        if (trimmed.startsWith("manga/") || trimmed.startsWith("genre/")) return null
        return trimmed
    }

    /** "Chapter 146" → "146"; "Ch 146.5" → "146.5"; tanpa angka → null. */
    fun parseChapterNumber(label: String?): String? {
        if (label.isNullOrBlank()) return null
        return Regex("""(?i)(?:chapter|ch)\s*([0-9]+(?:\.[0-9]+)?)""")
            .find(label)?.groupValues?.get(1)
            ?: Regex("""([0-9]+(?:\.[0-9]+)?)""").find(label)?.groupValues?.get(1)
    }

    fun parseSearch(html: String): List<SearchItem> {
        val doc = Jsoup.parse(html)
        return doc.select("div.bge").mapNotNull { el ->
            val href = el.select("div.bgei a[href], div.kan a[href]").first()?.attr("href")
            val slug = slugFrom(href) ?: return@mapNotNull null
            val title = el.select("div.kan h3").text().ifBlank { el.select("h3").text() }
            if (title.isBlank()) return@mapNotNull null
            val chapterLinks = el.select("div.kan div.new1 a[href]")
                .mapNotNull { chapterPath(it.attr("href")) }
            SearchItem(
                slug = slug,
                title = title,
                thumbUrl = el.select("div.bgei img").firstOrNull()
                    ?.let { absOrRaw(it.attr("src")) },
                type = el.select(".tpe1_inf b").text().ifBlank { null },
                firstChapter = chapterLinks.getOrNull(0),
                latestChapter = chapterLinks.getOrNull(1) ?: chapterLinks.lastOrNull(),
            )
        }.distinctBy { it.slug }
    }

    fun parseDetail(html: String, slug: String): Detail {
        val doc = Jsoup.parse(html)

        // "#Judul h1" → teks "Komik <span>Nama</span>" — lepas awalan "Komik ".
        val rawTitle = doc.select("#Judul h1").first()?.text().orEmpty()
        val title = rawTitle.removePrefix("Komik ").trim().ifBlank { slug }

        val thumbUrl = doc.select("#Informasi .ims img, .ims img").firstOrNull()
            ?.let { absOrRaw(it.attr("src")) }

        // Baris info <tr><td>Author:</td><td>Fuse</td></tr> — cocokkan global.
        var author: String? = null
        var status: String? = null
        for (row in doc.select("tr")) {
            val cells = row.select("td")
            if (cells.size < 2) continue
            when (cells[0].text().trim().trimEnd(':').lowercase()) {
                "author" -> if (author == null) author = cells[1].text().ifBlank { null }
                "status" -> if (status == null) status = cells[1].text().ifBlank { null }
            }
        }

        val genres = doc.select("li.genre a, ul.genre li a")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        // HTML asli: `</div> <h2>Sinopsis Lengkap …</h2> <p>…</p>` — paragraf
        // adalah SAUDARA setelah heading, sementara #Sinopsis hanya galeri
        // preview. Jaring dengan tiga lapis: sibling heading → #Sinopsis →
        // paragraf terpanjang yang wajar (40..1500 char, bukan CSS/JS inline).
        val synopsis = (
            doc.select("h2:contains(Sinopsis) ~ p, h3:contains(Sinopsis) ~ p").firstOrNull()?.text()
                ?: doc.select("#Sinopsis p").firstOrNull()?.text()
                ?: doc.select("p")
                    .map { it.text().trim() }
                    .filter { it.length in 40..1500 && '{' !in it }
                    .maxByOrNull { it.length }
            )?.trim()?.takeIf { it.length >= 40 }

        // <meta> di dalam <table> di-foster-parent Jsoup ke LUAR tabel, jadi
        // jangan ditala lewat #Daftar_Chapter — cari global saja.
        val totalChapters = doc.select("meta[itemprop=numberOfItems]")
            .firstOrNull()?.attr("content")?.trim()?.toIntOrNull()

        // Tabel terbaru-dulu; dedup per nomor mempertahankan unggahan terbaru
        // (occurrence pertama), lalu urutkan numerik naik.
        val rows = doc.select("#Daftar_Chapter tr[itemprop=itemListElement]")
        val parsed = rows.mapNotNull { row ->
            val a = row.select("td.judulseries a[href]").first() ?: row.select("a[href]").first()
            val path = chapterPath(a?.attr("href")) ?: return@mapNotNull null
            val label = a?.text().orEmpty().ifBlank { a?.attr("title").orEmpty() }
            ChapterItem(
                path = path,
                number = parseChapterNumber(label),
                title = label.ifBlank { null },
                dateText = row.select("td.tanggalseries").text().ifBlank { null },
            )
        }
        val deduped = LinkedHashMap<String, ChapterItem>()
        for (ch in parsed) {
            val key = ch.number?.toFloatOrNull()?.toString() ?: "path:${ch.path}"
            if (key !in deduped) deduped[key] = ch
        }
        val chapters = deduped.values.sortedWith(
            compareBy { ch: ChapterItem -> ch.number?.toFloatOrNull() ?: Float.MAX_VALUE }
                .thenBy { ch -> ch.number?.length ?: 0 },
        )

        return Detail(
            title = title,
            thumbUrl = thumbUrl,
            author = author,
            status = status,
            genres = genres,
            synopsis = synopsis,
            chapters = chapters,
            totalChapters = totalChapters,
        )
    }

    /**
     * Gambar halaman chapter: hanya `img.klazy` di dalam `#Baca_Komik`
     * (49 halaman asli per halaman ch. Tensura; iklan `komiku-promosi`,
     * ikon bendera, dan logo login TIDAK punya class `klazy`).
     */
    fun parseChapterImages(html: String): List<String> {
        val doc = Jsoup.parse(html)
        return doc.select("#Baca_Komik img.klazy")
            .map { it.attr("src").trim() }
            .filter { it.isNotEmpty() && !it.contains("promosi", ignoreCase = true) }
            .distinct()
    }

    private fun absOrRaw(src: String?): String? {
        val s = src?.trim().orEmpty()
        if (s.isEmpty()) return null
        if (s.startsWith("//")) return "https:$s"
        return s
    }
}
