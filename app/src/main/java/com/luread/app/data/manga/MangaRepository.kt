package com.luread.app.data.manga

import com.luread.app.data.komiku.KomikuApi
import com.luread.app.data.komiku.KomikuParser
import com.luread.app.data.network.ApiResult
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Repositori manga: memetakan DTO MangaDex ke model UI dan mengelola
 * kredibilitas attribution (wajib mencantumkan MangaDex).
 *
 * LUREAD-FEAT: sumber kedua **Komiku** ([komiku]) disusun sebagai berikut —
 * pencarian menyusulkan hasil Komiku setelah MangaDex (dan menolong saat
 * MangaDex gagal dijangkau); detail/chapter/gambar manga berprefiks
 * `komiku:` dilayani penuh oleh Komiku. Dua sumber tidak pernah dicampur
 * per-chapter agar chapter tidak ganda.
 *
 * LUREAD-FEAT: lubang nomor di daftar MangaDex (tautan eksternal yang
 * dibuang, chapter yang tidak pernah terbit di sana) **diisi dari Komiku**
 * lewat [fillFromKomiku] — pencarian + kecocokan judul [matchSlug], lalu
 * chapter Komiku yang nomornya belum ada disisipkan dan daftar diurutkan
 * ulang. Best-effort: kegagalan Komiku menyisakan daftar MangaDex apa
 * adanya.
 */
class MangaRepository(
    private val api: MangaDexApi,
    private val komiku: KomikuApi? = null,
) {

    /** Cache judul MangaDex per id untuk pencarian kecocokan Komiku
     *  (diisi saat detail dibuka; menghemat permintaan mangaById). */
    private val titleCache = ConcurrentHashMap<String, String>()

    /** Ringkasan manga untuk kartu. */
    data class MangaSummary(
        val id: String,
        val title: String,
        val coverUrl: String?,
        val authorNames: List<String>,
        val tags: List<String>,
        val status: String?,
        val description: String?,
        // LUREAD-FEAT: URL eksternal bila manga hanya ada di situs lain.
        val externalUrl: String? = null,
        // LUREAD-FEAT: sumber konten ("Komiku"); null = MangaDex.
        val source: String? = null,
    )

    /** Satu chapter untuk daftar. */
    data class ChapterSummary(
        val id: String,
        val number: String?,
        val title: String?,
        val language: String?,
        val pages: Int?,
    )

    /** Hasil halaman manga. */
    data class MangaPage(
        val results: List<MangaSummary>,
        val offset: Int,
        val total: Int,
        // LUREAD-FEAT: offset halaman berikutnya bisa diberi manual bila
        // results memuat sumber tambahan (Komiku) — matematika offset tetap
        // memakai konsumsi halaman MangaDex saja.
        val nextOffset: Int? = if (offset + results.size < total) offset + results.size else null,
    )

    fun toSummary(m: MangaDexManga): MangaSummary = MangaSummary(
        id = m.id,
        title = m.title,
        coverUrl = m.coverUrl,
        authorNames = m.authorNames,
        tags = m.tags,
        status = m.status,
        description = m.description,
        externalUrl = m.externalUrl,
    )

    /**
     * Cari manga. LUREAD-FEAT: halaman pertama juga menyusulkan hasil
     * pencarian Komiku di belakang MangaDex (offset MangaDex dipertahankan
     * untuk load-more). Bila MangaDex gagal dijangkau (mis. diblokir ISP) dan
     * Komiku hidup, pencarian tetap menampilkan hasil.
     */
    suspend fun search(title: String?, limit: Int = MangaDexApi.PAGE_SIZE, offset: Int = 0): ApiResult<MangaPage> {
        val mdex: ApiResult<MangaPage> = when (val r = api.searchManga(title, limit, offset)) {
            is ApiResult.Failure -> r
            is ApiResult.Success -> ApiResult.Success(
                MangaPage(
                    results = r.value.data.map { toSummary(it) },
                    offset = r.value.offset,
                    total = r.value.total,
                ),
            )
        }
        if (offset != 0 || title.isNullOrBlank() || komiku == null) return mdex

        val extras = when (val k = komiku.search(title)) {
            is ApiResult.Failure -> emptyList()
            is ApiResult.Success -> k.value.map { toKomikuSummary(it) }
        }
        return when {
            mdex is ApiResult.Success && extras.isEmpty() -> mdex
            mdex is ApiResult.Success -> ApiResult.Success(
                MangaPage(
                    results = mdex.value.results + extras,
                    offset = mdex.value.offset,
                    total = mdex.value.total + extras.size,
                    nextOffset = mdex.value.nextOffset,
                ),
            )
            extras.isNotEmpty() -> ApiResult.Success(MangaPage(extras, 0, extras.size))
            else -> mdex
        }
    }

    private fun toKomikuSummary(s: KomikuParser.SearchItem): MangaSummary = MangaSummary(
        id = KomikuApi.PREFIX + s.slug,
        title = s.title,
        coverUrl = s.thumbUrl,
        authorNames = emptyList(),
        tags = emptyList(),
        status = null,
        description = null,
        externalUrl = KomikuApi.detailUrl(s.slug),
        source = "Komiku",
    )

    suspend fun popular(limit: Int = MangaDexApi.PAGE_SIZE, offset: Int = 0): ApiResult<MangaPage> =
        when (val r = api.popularManga(limit, offset)) {
            is ApiResult.Failure -> r
            is ApiResult.Success -> ApiResult.Success(
                MangaPage(
                    results = r.value.data.map { toSummary(it) },
                    offset = r.value.offset,
                    total = r.value.total,
                ),
            )
        }

    suspend fun latest(limit: Int = MangaDexApi.PAGE_SIZE, offset: Int = 0): ApiResult<MangaPage> =
        when (val r = api.latestManga(limit, offset)) {
            is ApiResult.Failure -> r
            is ApiResult.Success -> ApiResult.Success(
                MangaPage(
                    results = r.value.data.map { toSummary(it) },
                    offset = r.value.offset,
                    total = r.value.total,
                ),
            )
        }

    /** Detail manga + chapter (untuk layar detail). */
    suspend fun detail(mangaId: String): ApiResult<Pair<MangaSummary, List<ChapterSummary>>> {
        // LUREAD-FEAT: manga berprefiks komiku: dilayani sumber Komiku.
        KomikuApi.slugOrNull(mangaId)?.let { slug -> return komikuDetail(slug) }
        val meta = when (val m = api.mangaById(mangaId)) {
            is ApiResult.Failure -> return m
            is ApiResult.Success -> toSummary(m.value)
        }
        return when (val c = chaptersOf(mangaId, preferTitle = meta.title)) {
            is ApiResult.Failure -> c
            is ApiResult.Success -> ApiResult.Success(meta to c.value)
        }
    }

    /** Detail Komiku → model UI (chapter: bahasa id, pages null = tanpa label). */
    private suspend fun komikuDetail(slug: String): ApiResult<Pair<MangaSummary, List<ChapterSummary>>> {
        val k = komiku
            ?: return ApiResult.Failure(com.luread.app.data.network.ApiError.NotFound("Komiku disabled"))
        return when (val r = k.detail(slug)) {
            is ApiResult.Failure -> r
            is ApiResult.Success -> {
                val d = r.value
                val summary = MangaSummary(
                    id = KomikuApi.PREFIX + slug,
                    title = d.title,
                    coverUrl = d.thumbUrl,
                    authorNames = listOfNotNull(d.author),
                    tags = d.genres,
                    status = d.status,
                    description = d.synopsis,
                    externalUrl = KomikuApi.detailUrl(slug),
                    source = "Komiku",
                )
                ApiResult.Success(summary to komikuChapters(d))
            }
        }
    }

    private fun komikuChapters(d: KomikuParser.Detail): List<ChapterSummary> =
        d.chapters.map { ch ->
            ChapterSummary(
                id = KomikuApi.PREFIX + ch.path,
                number = ch.number,
                title = ch.title,
                language = "id",
                // Jumlah halaman Komiku baru diketahui saat chapter dibuka;
                // null menyembunyikan label pp (bukan berarti 0 halaman).
                pages = null,
            )
        }

    /**
     * Ambil semua chapter — SEMUA bahasa dalam satu feed, lalu [dedupAndSort]
     * dan pengisian lubang dari Komiku ([fillFromKomiku], bila sumber aktif).
     *
     * LUREAD-FIX: percobaan lama `id`+`en` dulu membuat chapter yang hanya
     * ada di bahasa lain (mis. es-la/pt-br) hilang dari daftar — nomor jadi
     * lompat (kasus nyata "Tensei Shitara Slime datta Ken": 83, 126–128,
     * 131–136 tidak tampil). Semua bahasa digabung per nomor, jadi tidak
     * ada nomor yang terlewat selama masih ada di MangaDex.
     */
    suspend fun chapters(mangaId: String): ApiResult<List<ChapterSummary>> =
        chaptersOf(mangaId, preferTitle = null)

    private suspend fun chaptersOf(
        mangaId: String,
        preferTitle: String?,
    ): ApiResult<List<ChapterSummary>> {
        // LUREAD-FEAT: daftar chapter Komiku (dipakai navigasi prev/next
        // pembaca) diambil dari detail yang sama sehingga id konsisten.
        KomikuApi.slugOrNull(mangaId)?.let { slug ->
            val k = komiku
                ?: return ApiResult.Failure(com.luread.app.data.network.ApiError.NotFound("Komiku disabled"))
            return when (val r = k.detail(slug)) {
                is ApiResult.Failure -> r
                is ApiResult.Success -> ApiResult.Success(komikuChapters(r.value))
            }
        }
        val mdex = when (val all = fetchAllChapters(mangaId, emptyList())) {
            is ApiResult.Failure -> return all
            is ApiResult.Success -> dedupAndSort(all.value)
        }
        if (komiku == null) return ApiResult.Success(mdex)
        // Judul MangaDex untuk kecocokan ke Komiku: dari pemanggil (detail
        // baru saja mengambil meta) atau cache; mangaById hanya bila perlu.
        val title = preferTitle?.takeIf { it.isNotBlank() }
            ?: titleCache[mangaId]
            ?: when (val m = api.mangaById(mangaId)) {
                // Best-effort: daftar MangaDex tetap tampil tanpa pengisian.
                is ApiResult.Failure -> return ApiResult.Success(mdex)
                is ApiResult.Success -> m.value.title.also {
                    if (it.isNotBlank() && it != "Manga") titleCache[mangaId] = it
                }
            }
        return ApiResult.Success(fillFromKomiku(title, mdex))
    }

    /**
     * LUREAD-FEAT: isi lubang nomor daftar MangaDex dari Komiku.
     *
     * Beberapa nomor tidak pernah punya entri terbaca di MangaDex (tautan
     * eksternal yang dibuang oleh [dedupAndSort], keterlambatan terbit) —
     * pembaca melihat nomor melompat. Sumber Komiku dicocokkan ke judul
     * (jangan sampai manga salah judul/decoy!), lalu chapternya yang nomornya
     * belum ada disisipkan. Seluruh tahap bersifat best-effort: kegagalan
     * jaringan/kecocokan mengembalikan daftar MangaDex tanpa diubah.
     */
    private suspend fun fillFromKomiku(
        title: String,
        mdex: List<ChapterSummary>,
    ): List<ChapterSummary> {
        val k = komiku ?: return mdex
        if (title.isBlank()) return mdex
        val search = when (val r = k.search(title)) {
            is ApiResult.Failure -> return mdex
            is ApiResult.Success -> r.value
        }
        val slug = matchSlug(title, search.map { it.slug to it.title }) ?: return mdex
        val detail = when (val r = k.detail(slug)) {
            is ApiResult.Failure -> return mdex
            is ApiResult.Success -> r.value
        }
        return fillChapters(mdex, komikuChapters(detail))
    }

    /**
     * Sisipkan chapter [extras] (Komiku) yang nomornya belum ada di [mdex],
     * lalu gabung+urutkan ulang lewat [dedupAndSort]. Nomor label (tak
     * numerik) dan nomor yang sudah ada tidak ditambahkan — tidak pernah
     * menggandakan entri sumber MangaDex.
     */
    internal fun fillChapters(
        mdex: List<ChapterSummary>,
        extras: List<ChapterSummary>,
    ): List<ChapterSummary> {
        if (extras.isEmpty()) return mdex
        val present = mdex.mapNotNullTo(HashSet<Float>()) { it.number?.toFloatOrNull() }
        val missing = extras.filter { ch ->
            val n = ch.number?.toFloatOrNull()
            n != null && n !in present
        }
        if (missing.isEmpty()) return mdex
        return dedupAndSort(mdex + missing)
    }

    /**
     * Cocokkan judul MangaDex ke hasil pencarian Komiku. Kandidat pertama
     * dengan judul ternormalisasi sama menang; bila tidak ada, kontainmen
     * dua arah diizinkan hanya untuk selisih panjang ≤ 4 karakter (mencegah
     * decoy spin-off seperti "One Piece" → "One Piece: Ace Story").
     */
    internal fun matchSlug(title: String, candidates: List<Pair<String, String>>): String? {
        val t = normalizeTitle(title)
        if (t.isEmpty()) return null
        candidates.firstOrNull { normalizeTitle(it.second) == t }?.let { return it.first }
        return candidates.firstOrNull { (_, candidate) ->
            val c = normalizeTitle(candidate)
            t.length >= 6 && c.length >= 6 &&
                (c in t || t in c) &&
                abs(t.length - c.length) <= 4
        }?.first
    }

    /** Huruf kecil, `×`→`x`, buang semua selain a–z/0–9
     *  ("SPY×FAMILY" ≡ "Spy X Family" → "spyxfamily"). */
    private fun normalizeTitle(title: String): String =
        title.lowercase()
            .replace('×', 'x')
            .replace('✕', 'x')
            .replace(Regex("[^a-z0-9]+"), "")

    private suspend fun fetchAllChapters(
        mangaId: String,
        languages: List<String>,
    ): ApiResult<List<ChapterSummary>> {
        val all = mutableListOf<ChapterSummary>()
        var offset = 0
        var total = Int.MAX_VALUE
        // LUREAD-FIX: feed MangaDex hanya melayani offset ≤ 10.000 — berhenti
        // dengan data parsial lebih baik daripada gagal total kena 400.
        while (offset < total && offset <= 10_000) {
            val r = api.chapters(mangaId, limit = 100, offset = offset, languages = languages)
            when (r) {
                is ApiResult.Failure -> return r
                is ApiResult.Success -> {
                    total = r.value.total
                    val batch = r.value.data
                    if (batch.isEmpty()) break
                    all += batch.map { ch ->
                        ChapterSummary(
                            id = ch.id,
                            number = ch.attributes?.chapter,
                            title = ch.attributes?.title,
                            language = ch.attributes?.translatedLanguage,
                            pages = ch.attributes?.pages,
                        )
                    }
                    offset += batch.size
                }
            }
        }
        return ApiResult.Success(all)
    }

    /**
     * LUREAD-FIX: chapter digabung lintas bahasa per NOMOR — satu nomor satu
     * entri terbaik (kasus nyata: ch. 138 tampil dobel id+en, dan versi en-nya
     * tautan eksternal tanpa gambar). Chapter tanpa halaman (pages=0, hampir
     * selalu tautan eksternal resmi seperti KManga) DIBUANG karena tidak bisa
     * dibaca — daftar tidak pernah memamerkan entri yang membuka error
     * "No pages in this chapter".
     *
     * Pemenang satu nomor: bahasa id > en > lainnya; seri → halaman terbanyak.
     * Nomor kosong (null) tidak digabung (bisa banyak oneshot); label
     * non-numerik (mis. "extra") digabung per label.
     * Sort: nomor numerik naik (bukan leksikografis), lalu bahasa, lalu
     * nomor tak terbaca/null di akhir daftar.
     *
     * Filter halaman: `pages == 0` (explicit) = tautan eksternal tak
     * terbaca → DIBUANG; `pages == null` = jumlah halaman belum diketahui
     * (chapter Komiku — label pp disembunyikan, tetap bisa dibaca) →
     * dipertahankan.
     */
    internal fun dedupAndSort(chapters: List<ChapterSummary>): List<ChapterSummary> {
        val best = LinkedHashMap<String, ChapterSummary>()
        for (ch in chapters) {
            if (ch.pages == 0) continue
            val number = ch.number
            val key = when {
                number == null -> "id:${ch.id}"
                number.toFloatOrNull() != null -> "n:${number.toFloat()}"
                else -> "l:${number.lowercase()}"
            }
            val existing = best[key]
            if (existing == null || preferChapter(ch, existing)) {
                best[key] = ch
            }
        }
        return best.values.sortedWith(
            compareBy<ChapterSummary> { ch -> ch.number?.toFloatOrNull() ?: Float.MAX_VALUE }
                .thenBy { ch -> langRank(ch.language) }
                .thenBy { ch -> ch.number?.length ?: 0 },
        )
    }

    /**
     * Pemenang satu nomor. Kandidat pages==0 sudah difilter pemanggil,
     * jadi bahasa didahulukan: id > en > lainnya; seri → halaman terbanyak
     * (null = belum diketahui, dianggap paling kecil).
     */
    private fun preferChapter(candidate: ChapterSummary, current: ChapterSummary): Boolean {
        val candidateRank = langRank(candidate.language)
        val currentRank = langRank(current.language)
        if (candidateRank != currentRank) return candidateRank < currentRank
        return (candidate.pages ?: 0) > (current.pages ?: 0)
    }

    private fun langRank(lang: String?): Int = when (lang) {
        "id" -> 0
        "en" -> 1
        else -> 2
    }

    /** URL gambar halaman chapter (kualitas data-saver agar hemat kuota). */
    suspend fun pageUrls(chapterId: String): ApiResult<List<String>> {
        // LUREAD-FEAT: chapter Komiku diambil dari halaman bacanya langsung.
        KomikuApi.chapterOrNull(chapterId)?.let { path ->
            val k = komiku
                ?: return ApiResult.Failure(com.luread.app.data.network.ApiError.NotFound("Komiku disabled"))
            return k.pages(path)
        }
        return when (val r = api.atHome(chapterId)) {
            is ApiResult.Failure -> r
            is ApiResult.Success -> {
                val chapter = r.value.chapter
                val base = r.value.baseUrl
                if (chapter == null || base.isBlank()) {
                    ApiResult.Failure(com.luread.app.data.network.ApiError.NotFound("No image data"))
                } else {
                    val urls = chapter.dataSaver.mapIndexedNotNull { i, _ ->
                        chapter.pageUrl(base, i, saver = true)
                    }.ifEmpty {
                        chapter.data.mapIndexedNotNull { i, _ -> chapter.pageUrl(base, i, saver = false) }
                    }
                    ApiResult.Success(urls)
                }
            }
        }
    }
}
