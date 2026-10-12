# Audit API Gutendex (12 Oktober 2026)

Semua klaim di dokumen ini berasal dari request nyata ke `https://gutendex.com` yang responsnya disimpan sebagai fixture (`/tmp/audit/gutendex/*.headers` + `*.json`). Tidak ada angka yang dikarang.

Catatan lingkungan: dari host ini, `gutendex.com` **harus diakses dengan `curl -4`**. Resolusi IPv6 mengarah ke blackhole (koneksi hangus, `HTTP 000`). Sesekali server membalas `000` bahkan setelah retry → permintaan diulang dengan jeda.

## 1. Envelope katalog

`GET /books` (tanpa slash) membalas **301** ke `/books/` (`t1_root_noslash.headers`). Bentuk envelope (`t1_root_noslash.json`):

```json
{
  "count": 79582,
  "next": "https://gutendex.com/books/?page=2",
  "previous": null,
  "results": [ ... 32 objek buku ... ]
}
```

| Sifat | Hasil pengamatan | Bukti |
| --- | --- | --- |
| Jumlah hasil per halaman | **32** (selalu, di semua fixture OK) | semua `*.json` OK |
| `next` / `previous` | URL **penuh** (`https://gutendex.com/books/?…`) atau `null` | t1, t7 (page 2 punya keduanya) |
| Envelope error | Bukan JSON — **HTML** | t8, t11, t12, t13 |

## 2. Field buku (dianalisis dari 280 entri, 172 buku unik)

Script analisis: `/tmp/audit/analyze.py`. Field yang **selalu** muncul di 280/280 entri dengan tipe konsisten:

`id: int`, `title: str`, `authors: list`, `summaries: list`, `editors: list`, `translators: list`, `subjects: list`, `bookshelves: list`, `languages: list`, `copyright: bool`, `media_type: str`, `formats: dict`, `download_count: int`.

Field person (`name`, `birth_year`, `death_year`): 286 kemunculan; `birth_year` null 6×, `death_year` null 8× → **wajib nullable**.

Temuan lain dari sampel:

* Buku **tanpa penulis**: 14 dari 280.
* Buku **tanpa summary**: 10 dari 280.
* `copyright: true`: 10 dari 280.
* `media_type`: `Text` 271, `Sound` 9.
* Tidak ada buku multi-bahasa di sampel (tapi field tetap list).
* URL format: **semua HTTPS** (2127 URL).

## 3. Format yang benar-benar tersedia (frekuensi di sampel 280 entri)

| MIME | Kemunculan |
| --- | --- |
| `text/html` | 280 |
| `application/rdf+xml` | 280 |
| `image/jpeg` (cover) | 280 |
| `application/octet-stream` | 276 |
| `application/epub+zip` | 271 |
| `application/x-mobipocket-ebook` | 271 |
| `text/plain; charset=utf-8` | 270 |
| `text/plain; charset=us-ascii` | 167 |
| `audio/mpeg` / `audio/ogg` / `audio/mp4` | 9 / 5 / 5 |
| `text/html; charset=utf-8` | 7 |
| **`application/pdf`** | **3** |
| `application/msword` | 2 |
| `text/x-rst` | 1 |

Kesimpulan penting yang membentuk keputusan pembaca:

* **PDF sangat langka** (3/184 buku teks di sampel) → PDF tetap didukung lewat `PdfRenderer` untuk file hasil impor lokal, bukan untuk katalog.
* Varian MIME memuat **parameter charset** (`text/plain; charset=utf-8`) → pemilihan format harus menormalkan parameter (implementasi: `data/catalog/Formats.kt::normalizeMime`).
* Ada dua varian `text/plain` (utf-8 dan us-ascii) → pemilih format memprioritaskan `charset=utf-8`.

Pola URL yang diamati (digit dinormalisasi):

```
https://www.gutenberg.org/ebooks/N.epubN.images      (EPUB, 271×)
https://www.gutenberg.org/ebooks/N.html.images        (HTML, 271×)
https://www.gutenberg.org/ebooks/N.txt.utf-N          (TXT, 270×)
https://www.gutenberg.org/cache/epub/N/pgN.cover.medium.jpg  (cover, 280×)
https://www.gutenberg.org/ebooks/N.rdf                (metadata, 280×)
https://www.gutenberg.org/cache/epub/N/pgN-h.zip      (zip HTML, 205×)
https://www.gutenberg.org/files/N/N-N.txt             (TXT alternatif, 153×)
```

URL **tidak pernah** boleh disusun dari ID buku sendiri — selalu pakai URL yang dikembalikan API.

## 4. Parameter query yang diuji

| Parameter | Nilai uji | Status | `count` | Catatan |
| --- | --- | --- | --- | --- |
| (tanpa parameter) | — | **200** (via 301) | 79582 | halaman pertama |
| `search` | `sherlock` | **200** | 23 | `next=null` |
| `search` | (string kosong) | **200** | 0 | hasil kosong, bukan error |
| `languages` | `en` | **200** | 63192 | `t20_lang_en.json` |
| `languages` | `fr` | **200** | 4257 | `t20_lang_fr.json` |
| `languages` | `de` | **200** | 2472 | `t20_lang_de.json` |
| `languages` | `es` | **200** | 922 | `t20_lang_es.json` |
| `languages` | `it` | **200** | 1116 | `t20_lang_it.json` |
| `languages` | `nl` | **200** | 1121 | `t20_lang_nl.json` |
| `languages` | `pt` | **200** | 655 | `t20_lang_pt.json` |
| `languages` | `ru` | **200** | 9 | `t20_lang_ru.json` |
| `languages` | `zh` | **200** | 444 | `t22_languages_zh.json` (attempt pertama timeout, retry sukses) |
| `languages` | `fi` | **200** | 3693 | `t20_lang_fi.json` |
| `topic` | `children` | **200** | 8224 | `t4_topic_children.json` |
| `topic` | `poetry` | **200** | 5599 | `t22_topic_poetry.json` (attempt pertama timeout, retry sukses) |
| `ids` | `1342` | **200** | 1 | |
| `mime_type` | `text/html` | **200** | 79117 | |
| `mime_type` | `application/epub+zip` | **200** | 78051 | fixture `probe2.json` |
| `page` | `2` | **200** | 79579 | `previous` terisi |
| `page` | tidak valid (contoh uji `t8`) | **503** + HTML | — | bukan JSON → jangan diulang sebagai retry JSON |
| `ids` | `1342,84,11` (multi) | **503** + HTML | — | multi-id **tidak dipakai** |
| `mime_type` | `text/plain` | **503** + HTML | — | tidak ditawarkan di UI |
| `sort` | (nilai uji) | **503** + HTML | — | **tidak dipakai**; UI tidak menampilkan kontrol sorting palsu |

Perhatikan juga bahwa `count` berubah antar hari (mis. `en`: 63191 pagi hari → 63192 beberapa jam kemudian) → angka katalog **tidak boleh di-hardcode**.

Bahasa yang diverifikasi 200 pada 2026-10-12 dan karenanya **satu-satunya** yang ditawarkan di filter UI:
`en, fr, de, es, it, nl, pt, ru, zh, fi`. Topik yang ditawarkan: `children, poetry`.
Bahasa/topik lain (mis. `ja`, `nonfiction`) **tidak ditawarkan** karena tidak terverifikasi 200 dari lingkungan ini — tidak ada asumsi.

## 5. Perilaku error & rate limiting

* Error API (parameter tak dikenal, id multi, halaman di luar jangkauan, `mime_type=text/plain`, `sort`) = **HTTP 503 dengan body HTML**, bukan JSON.
* Burst permintaan (beberapa request beruntun) memicu **503** juga.
* Konsekuensi implementasi (`data/network/GutendexApi.kt`):
  * `expectSuccess = false` → status dicek manual.
  * Retry **hanya** untuk Network/Timeout (2× backoff 400→900 ms) dan **1×** untuk 503.
  * Body non-JSON → `ApiError.NotJson` dan **tidak diulang** (mengulang hanya memperbesar kemungkinan 503 beruntun).
  * `next`/`previous` hanya diikuti bila URL-nya berasal dari host Gutendex (anti-SSRF sederhana, `isTrustedCatalogUrl`).
  * `User-Agent` yang dikenali dikirim (`USER_AGENT`).

## 6. Ringkasan keputusan yang diambil dari audit ini

1. Model DTO mengikuti field riil, semua person/summary nullable (`GutendexDtos.kt`).
2. Page size 32 diperlakukan sebagai konstanta API, bukan asumsi UI.
3. Pemilihan format: EPUB → HTML → TXT → PDF (`Formats.bestReadable`).
4. Filter UI = kombinasi parameter yang **terbukti 200** di atas. Tidak ada `sort`, tidak ada `mime_type=text/plain`, tidak ada `ids` multi.
5. Pagination infinite scroll memakai `next` URL penuh dari API (dipercaya hanya bila host cocok).
6. Tidak ada klaim kelengkapan katalog: `count` ditampilkan apa adanya dari API.

## 7. Yang belum diverifikasi

* `authors` parameter (filter per penulis) — **tidak diuji**, jadi tidak ditampilkan di UI.
* `bookshelves` filter — field ada pada buku, tetapi parameter query-nya tidak diuji → tidak ditampilkan sebagai filter.
* Perilaku setelah burst panjang / token bucket (hanya diamati: burst → 503).
* Stabilitas jangka panjang endpoint (dicek sekali pada 2026-10-12).
