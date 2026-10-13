# Feature Matrix — Luread

Tanggal: 13 Oktober 2026. Status mengikuti definisi di `docs/research.md`.
Paling penting: **"Implemented" ≠ "Tested"**. *Tested* = unit test lulus di CI + pemeriksaan statis + **kompilasi sukses di CI**. Tidak ada fitur yang diuji pada perangkat; jalur runtime (Readium/PDF/WebView/unduhan/MangaDex) tetap *Implemented, Not Tested on device* meski kompilasi terverifikasi.

Legenda: `V` Verified (sumber/audit), `I` Implemented (kode ada), `IT` Implemented-Not-Tested, `P` Planned (belum dibuat), `—` tidak berlaku.

## 0. Sumber data (setelah hapus Wiki)

| Sumber | Status | Catatan |
| --- | --- | --- |
| Project Gutenberg (Gutendex) | V/I | Audit penuh |
| Open Library | V/I | Audit curl |
| Internet Archive | V/I | Audit curl; termasuk komik `subject:comic books` |
| Wikisource / Wikibooks | **DIHAPUS** | Arahan user: jangan pakai API Wiki sama sekali |
| MangaDex (manga) | V/I | Tanpa key; attribution wajib; rate-limit 400ms |

## 1. Katalog & jaringan

| Fitur prompt | Status | Bukti / catatan jujur |
| --- | --- | --- |
| Gutendex sebagai sumber | V | Audit penuh, `docs/gutendex-api-audit.md` |
| Open Library sebagai sumber | V | Uji curl penuh, `docs/book-apis-audit.md` §1 |
| Internet Archive sebagai sumber | V | Uji curl penuh, §2; filter bahasa ISO 639-2; **topic (`subject:…`) didukung** |
| Filter sumber (chip "Source") | I | `DiscoverScreen`; 3 sumber (Wiki dihapus) |
| Search | I | Debounce 400 ms |
| Pagination | I | Kursor per sumber |
| Infinite scroll terkendali | I | Item terakhir ≤ total−4 |
| Language filter | I | 48 bahasa di UI; 65 pasangan ISO 639-2 di `CatalogLanguages` |
| Subject/topic filter | I | 39 topik (termasuk comic books/comics/graphic novels); Gutenberg via `topic`, OL/IA via `subject:` di query |
| Format filter | I | 10 format; baris hanya untuk Gutenberg kecuali sumber mendukung |
| Retry / error state | I | Network/Timeout 3×, 503/429 2× |
| In-memory cache 5 menit per kunci query | I | `CatalogRepository.pageCache` |

## 2. Manga (MangaDex)

| Fitur | Status | Catatan |
| --- | --- | --- |
| Browse manga populer | I | `order[followedCount]` |
| Search manga | I | Debounce 400 ms di `MangaListViewModel` |
| Infinite scroll | I | SnapshotFlow grid |
| Detail (cover, author, tag, status, deskripsi, chapter) | I | `GET /manga/{id}` + `GET /manga/{id}/feed` (id/en) |
| Baca chapter (pager gambar) | I | `GET /at-home/server/{id}`, data-saver fallback original |
| Attribution MangaDex + scanlation | I | String `manga_credit` di detail |
| Baris Manga di Home | I | Bisa dimatikan di Settings |

## 3. Komik (Internet Archive)

| Fitur | Status | Catatan |
| --- | --- | --- |
| Home "Komik & Comic Books" | I | `subject:comic books` + format readable |
| Filter topik komik di Discover | I | comic books / comics / graphic novels |
| API Komik pihak ketiga (OmegaAPI, Komiku REST, dll.) | **TIDAK DIPAKAI** | Uji curl: `DEPLOYMENT_DISABLED` / 403 AI-detector / 404; tidak ada backend sendiri |

## 4. Perpustakaan & impor

| Fitur | Status |
| --- | --- |
| Daftar + filter All/Offline/Finished/Favorites | I |
| Import file via SAF multi-format | I |
| **Impor arsip ZIP** — ekstrak & impor EPUB/HTML/TXT/PDF/CBZ di dalamnya | I (`LibraryViewModel.importZipContents`) |
| Validasi magic byte | I |
| Remove / favorite / progress | I |

## 5. Reader

| Fitur | Status | Catatan |
| --- | --- | --- |
| EPUB 2/3 via Readium 3.4.0 | IT | |
| PDF via `PdfRenderer` | IT | Tanpa seleksi teks |
| TXT (paginasi internal) | IT | Unit test CI |
| HTML lokal (WebView) | IT | **Background putih mutlak** (`injectWhiteCss` + bg putih) |
| CBZ (ekstrak gambar) | IT | |
| Manga (gambar URL MangaDex) | I | `MangaReaderScreen` HorizontalPager |
| Resume posisi, auto-save, bookmark, anotasi | IT | |
| Inline translate EPUB (cap 4000 char) | IT | Anti-lag |
| Line-height / dim overlay / page animation | I | Dari Settings |

## 6. Kustomisasi (Settings super lengkap)

| Fitur | Status |
| --- | --- |
| Tema Light/Dark/System | I |
| Aksen preset (4) | I |
| **Warna aksen kustom (swatch ARGB)** | I |
| Radius kartu Soft/Rounded/Pill | I |
| Jumlah kolom grid 2–5 | I (dipakai Discover + Manga) |
| Rasio cover | I |
| Metadata ringkas kartu | I |
| Kurangi animasi | I |
| Tampilkan baris Manga di Home | I |
| Ukuran teks 14–30 sp | IT |
| Line-height 1.0–2.2× | I (TextPager) |
| Dim overlay 0–70% | I (ReaderScreen) |
| Animasi halaman | I |
| Keep screen on / statistik | I |
| Bahasa target terjemahan (id/en/ms/ja) | I (tersimpan; dipakai translator) |

## 7. Privasi & infrastruktur

| Item | Status |
| --- | --- |
| Tanpa akun & API key | V/I |
| HTTPS saja, host tepercaya | I |
| Tanpa SDK analitik/iklan | V |
| CI workflow + unit test + assembleDebug | V |
| Logo resmi via `integrateLogo` | I |

