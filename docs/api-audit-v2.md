# Audit API v2 — Luread (2026-10-15)

Audit ulang seluruh endpoint yang dipakai aplikasi, dijalankan dua script
(`audit_api.py` 46 probe umum + `audit_app.py` 14 probe spesifik aplikasi)
dari lingkungan yang sama dengan pengembangan. Hasil mentah:
`/tmp/opencode/audit_results.json`, `/tmp/opencode/audit_app_results.json`.

Kesimpulan besar:

| Sumber | Status | Catatan |
|---|---|---|
| Internet Archive (search/metadata/img) | ✅ 200 | `download/{id}/{file}` → 302 ke node lalu 200 (503 pada audit = sesaat; unduhan sudah diberi retry 3×) |
| MangaDex (manga/feed/at-home) | ✅ 200 | DNS/SSL kadang diblokir lingkungan untuk `uploads.mangadex.org` (bukan salah aplikasi) |
| Open Library (search/works/authors/isbn/subjects/covers) | ✅ 200 | `q=subject:komik&language=id` → `numFound:0` (sumber tak berguna untuk Komik Indonesia) |
| Gutendex (plain/search/ids/topic=comics) | ⚠️ 200/503 | `mime_type=*`, `topic=children`, `search=…&languages=id` → 503; `sort=popular` & `page=2` timeout |
| translate.googleapis.com (gtx) | ⚠️ 429 | rate-limited; jaring MyMemory (200) sudah menjadi fallback |
| archive.org `download/…_djvu.txt` (probe) | ⚠️ 503 | sesaat; live re-check 2026-10-15 → 302→200 OK, ditangani retry DownloadManager |
| `bse.kemdikbud.go.id`, `repositori.kemdikbud.go.id`, `komiku.id` | ❌ DNS gagal | domain tidak bisa diakses dari jaringan uji — tidak dipakai aplikasi |
| `webtoons.com/id`, `mangaplus.shueisha.co.jp` | ✅ 200 | hanya pembanding, tidak dipakai |

## Perubahan perilaku aplikasi dari audit ini

1. **Komik Indonesia** (baris Home) → Internet Archive
   `subject:"komik indonesia" AND language:ind` (200, hasil nyata).
   Open Library dihindari untuk topik ini (`numFound:0`).
2. **Gutendex** `mime_type`/`topic=children` tidak pernah dikirim aplikasi
   (terbukti 503).
3. **Translate**: gtx (429) → MyMemory (200) sudah berurutan benar;
   timeout per-request kini mengikuti Pengaturan → Jaringan.
4. **MangaDex**: `externalUrl` dipakai sebagai fallback saat feed kosong;
   endpoint `manga`, `feed`, `at-home` terverifikasi 200 (400/404 untuk
   input salah sesuai harapan).

## Rincian probe

### Internet Archive

- `advancedsearch.php?q=subject:"komik indonesia" AND language:ind…&output=json` → **200**
- `advancedsearch.php?q=subject:"comic books" AND language:ind…` → **200**
- `advancedsearch.php?q=title:komik AND language:id` → **timeout** (tidak dipakai)
- `metadata/{id}` → **200** (`metadata` + `files`)
- `services/img/{id}` → **200** image
- `download/{id}/{file}` → **302** (node `dn*.archive.org`) lalu **200**;
  probe HEAD sesaat 503 → ditangani `MAX_ATTEMPTS=3` + backoff di DownloadManager

### MangaDex (`api.mangadex.org`)

- `/manga?title=…&contentRating[]=safe&includes[]=cover_art,author` → **200**
- `/manga/{uuid}` → **200**; `/manga/{uuid}` tidak ada → **404**; bukan UUID → **404**
- `/manga/{uuid}/feed?translatedLanguage[]=id,en&order[chapter]=asc` → **200**
- `/at-home/server/{chapterId}` → **200** (baseUrl + hash + data[])
- `/manga?limit=500` → **400** (limit max 100 — aplikasi membatasi 100 ✓)
- `/manga?sort=followedCount` tanpa `contentRating` → **400**
- `uploads.mangadex.org` → gagal DNS/SSL **di lingkungan uji** (blockpage);
  gambar dimuat via Coil dengan cache fallback di perangkat

### Open Library

- `search.json?q=sherlock` → **200**; `q=subject:komik&language=id` → **200** tetapi
  `numFound:0` → **tidak dipakai untuk Komik Indonesia**
- `/works/{id}.json`, `/authors/{id}.json`, `/isbn/{isbn}.json` → **200**
- `subjects/komik.json`, `subjects/komik_indonesia.json` → **200**
- `covers.openlibrary.org/b/id/{id}-M.jpg` (dan `-S`, id tak ada) → **200**

### Gutendex

- `/books/?limit=1`, `search=sherlock`, `languages=en`, `ids=1342`, `topic=comics` → **200**
- `mime_type=text/html|application/octet-stream|text/plain` → **503** (tidak dikirim aplikasi)
- `topic=children` → **503** (tidak dikirim aplikasi)
- `search=komik&languages=id` → **503** (filter `languages` di aplikasi tetap
  dipakai untuk buku berbahasa Inggris — terverifikasi 200 untuk `languages=en`)
- `sort=popular`, `page=2` → **timeout** (aplikasi tidak memakai `sort`)

### Terjemahan

- `translate.googleapis.com/translate_a/single?client=gtx…` → **429** (rate limit)
- `api.mymemory.translated.net/get?q=hello&langpair=en|id` → **200**
- Urutan aplikasi: gtx → MyMemory sudah benar; 429 jatuh ke provider berikutnya

### Domain yang tidak bisa diakses (jaringan uji, tidak dipakai aplikasi)

- `bse.kemdikbud.go.id`, `repositori.kemdikbud.go.id` → DNS gagal
- `komiku.id` → DNS gagal

## Endpoint yang dipakai aplikasi (pemetaan kode → API)

| Kode | Endpoint | Hasil audit |
|---|---|---|
| `data/network/GutendexApi.kt` | `GET gutendex.com/books/` | 200 (query aman) |
| `data/network/OpenLibraryApi.kt` | `GET openlibrary.org/search.json`, `/works`, `/authors`, `/isbn`, `/subjects` | 200 |
| `data/network/ArchiveApi.kt` | `GET archive.org/advancedsearch.php`, `/metadata/{id}` | 200 |
| `data/catalog/ArchiveFormats.kt` | `GET archive.org/download/{id}/{file}` | 302→200 (retry) |
| `data/manga/MangaDexApi.kt` | `GET api.mangadex.org/manga`, `/feed`, `/at-home/server/{id}` | 200 |
| `data/translate/TranslationService.kt` | `GET translate.googleapis.com/…`, `GET api.mymemory.translated.net/get` | 429→200 (fallback) |
| `data/downloads/DownloadManager.kt` | `HttpURLConnection` ke URL format buku | 302→200, retry 3×, redirect manual |

## Perubahan jaringan yang diimplementasikan

- **Retry unduhan** 3× dengan backoff 750ms×n (untuk 503 sesaat IA) — `DownloadManager.kt`
- **Redirect manual** (maks 5) mengikuti `Location` lintas node/protokol — `DownloadManager.kt`
- **Timeout dari Pengaturan** (`timeoutSeconds`, 10–120s) diterapkan per-request lewat
  `NetworkTimeout.applyTo` di 5 titik pusat fetch + timeout unduhan — `Http.kt`, `LureadApp.kt`
- **Pesan galat unduhan** disimpan per buku (`DownloadManager.errors`) dan ditampilkan
  di layar detail — sebelumnya senyap
