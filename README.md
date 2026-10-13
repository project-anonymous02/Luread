# Luread

Perpustakaan digital pribadi & pembaca ebook untuk Android. Katalog gratis dari **Project Gutenberg** (Gutendex), **Open Library**, **Internet Archive**, plus **MangaDex** (manga, baca chapter gambar) — semua **tanpa akun dan tanpa API key**. Tanpa iklan/analitik. Data buku, catatan, dan statistik tinggal di perangkat.

> **Status proyek (13 Okt 2026):** CI GitHub Actions membangun setiap push; unit test & `assembleDebug` berjalan otomatis. Yang **belum** diuji: runtime di perangkat/emulator (tidak ada perangkat di lingkungan ini). Dokumen fitur: `docs/feature-matrix.md`.

## Fitur yang ada

* **Discover** — pencarian 3 sumber buku (Gutenberg, Open Library, Internet Archive) lewat chip **Source**, filter bahasa (48+)/topik (39 termasuk komik)/format (10), pagination & infinite scroll. Rincian uji endpoint: `docs/book-apis-audit.md`.
* **Manga (MangaDex)** — cari/browse manga populer, daftar chapter (id/en), baca chapter sebagai pager gambar (at-home server). Kredit MangaDex + scanlation group ditampilkan.
* **Komik & Comic Books** — baris Home dari Internet Archive (`subject:comic books`), PDF/CBZ yang bisa diunduh.
* **Home multi-section** — Continue, Featured (Gutenberg), Cerita Indonesia (Open Library `language=id`), Anak-anak, Rekomendasi, Pengetahuan, Manga, Komik, Recently added.
* **Detail buku** — metadata apa adanya; field kosong disembunyikan (tidak ditebak).
* **Unduhan** — progres byte nyata, batal, validasi magic byte.
* **Perpustakaan lokal** — impor file via SAF (EPUB/PDF/TXT/HTML/CBZ) **atau arsip ZIP** (diekstrak; file yang bisa dibaca di dalamnya diimpor satu per satu).
* **Pembaca** — EPUB 2/3 (Readium 3.4.0), PDF (`PdfRenderer`), TXT, HTML lokal (WebView **putih mutlak**), CBZ, Manga (gambar jarak jauh). Resume posisi, bookmark, anotasi.
* **Aktivitas & statistik** — sesi membaca nyata.
* **Penerjemah** — halaman Translator + terjemahan inline di EPUB (cap 4000 karakter agar tidak lag).
* **Personalisasi super lengkap** — tema, preset aksen **+ warna kustom**, radius kartu, jumlah kolom grid (2–5), rasio cover, metadata ringkas, kurangi animasi, tampilkan/sembunyikan baris Manga di Home, ukuran teks 14–30 sp, line-height 1.0–2.2×, dim overlay 0–70%, animasi halaman, keep-screen-on, statistik on/off, bahasa target terjemahan.

Daftar lengkap beserta status jujur (Implemented / Not Tested / Planned): **`docs/feature-matrix.md`**.

## Cara build

Prasyarat: JDK 17, Android SDK (`compileSdk` 37), koneksi internet.

```bash
python3 scripts/verify.py      # pemeriksaan statis (tanpa SDK, detik)
./gradlew testDebugUnitTest    # unit test JVM
./gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
```

Atau lewat GitHub Actions: tab **Actions** → **Build** → **Run workflow** (jalan otomatis pada setiap push/PR). Artifact `luread-debug-apk` + `test-reports`.

**Jangan jalankan gradle lokal bila tidak perlu** — CI sudah kompilasi + test. Lokal cukup `verify.py` lalu push.

Catatan wrapper: `gradlew`, `gradlew.bat`, dan `gradle/wrapper/gradle-wrapper.jar` adalah **file resmi Gradle** (tag `v9.6.0`). Bila executable bit hilang: `chmod +x gradlew`.

## Struktur

```
app/src/main/java/com/luread/app/
├── LureadApp.kt        Application + AppContainer (DI manual)
├── MainActivity.kt     Navigasi + route (Home/Discover/Library/Activity/Settings/Manga*)
├── core/               brand, sistem desain, util
├── data/               network (Ktor), catalog, manga (MangaDex), Room, DataStore, downloads
└── ui/                 layar + mesin reader + manga (list/detail/reader)
docs/                   riset, audit API, arsitektur, fitur
scripts/verify.py       pemeriksaan statis (lokal & CI)
.github/workflows/build.yml
```

## Batasan teknis (jujur)

1. **Kompilasi & unit test di CI; runtime belum diuji di perangkat.**
2. **Wiki (Wikisource/Wikibooks) dihapus total** — arahan user: jangan pakai API Wiki.
3. **API Komik terpisah** (OmegaAPI/Komiku REST dll.) mati (`DEPLOYMENT_DISABLED`) → komik diambil dari **Internet Archive** (`subject:comic books`). Tidak ada backend.
4. **Sorting katalog tidak ada** — parameter `sort` Gutendex terbukti 503.
5. **PDF tidak mendukung seleksi teks** (keterbatasan `PdfRenderer`).
6. **MangaDex attribution wajib** — kredit MangaDex + scanlation group; tanpa iklan/bayar (syarat API).
7. **R8/ProGuard dinonaktifkan** pada release sampai konfigurasi terverifikasi.
8. **`app/src/androidTest/` tidak dibuat** — tidak ada emulator.

## Privasi

Tanpa akun, tanpa API key, tanpa SDK analitik/iklan/crash. Jaringan hanya ke host katalog tepercaya (`gutendex.com`, `openlibrary.org`, `archive.org`, `api.mangadex.org`, `uploads.mangadex.org`) + URL yang dikembalikan API. Izin: `INTERNET`, `WAKE_LOCK`.

## Dokumen

| Dokumen | Isi |
| --- | --- |
| `docs/research.md` | Versi dependency & batasan lingkungan |
| `docs/gutendex-api-audit.md` | Audit Gutendex dari request nyata |
| `docs/book-apis-audit.md` | Audit Open Library & Internet Archive |
| `docs/ui-research.md` | Keputusan desain |
| `docs/architecture.md` | Arsitektur & aliran data |
| `docs/feature-matrix.md` | Status tiap fitur (jujur) |
| `docs/testing.md` | Apa yang diuji, apa yang tidak |
| `docs/translation-integration.md` | Integrasi penerjemah |
