# Luread

Perpustakaan digital pribadi & pembaca ebook untuk Android. Katalog gratis dari **Project Gutenberg** lewat API **Gutendex**, tanpa akun, tanpa API key, tanpa iklan/analitik. Data buku, catatan, dan statistik tinggal di perangkat.

> **Status proyek (13 Okt 2026):** **CI GitHub Actions hijau** (run [`38326003012`](https://github.com/project-anonymous02/Luread/actions/runs/38326003012), commit `1dcdba8`): pemeriksaan statis lolos, **35 unit test lulus**, dan `assembleDebug` menghasilkan artifact `luread-debug-apk` (~33 MB) + `test-reports`. Kompilasi Kotlin/Room KSP/Readium terverifikasi di CI. Yang **belum** diuji: runtime di perangkat/emulator (tidak ada perangkat di lingkungan ini). Dokumen fitur: `docs/feature-matrix.md`.

## Fitur yang ada

* **Discover** — pencarian katalog, filter bahasa/topik/format (hanya parameter API yang terverifikasi), pagination & infinite scroll, hitung jumlah hasil dari API.
* **Detail buku** — metadata apa adanya (penulis, bahasa, topik, ringkasan, hak cipta, daftar format). Bila sebuah data tidak tersedia, tidak ditampilkan (tidak ditebak).
* **Unduhan** — progres byte nyata, batal, validasi magic byte (EPUB/zip & PDF) sebelum ditandai selesai.
* **Perpustakaan lokal** — tambah dari katalog atau impor file via SAF (EPUB/PDF/TXT/HTML), filter, favorit, hapus metadata tanpa menghapus file kecuali diminta.
* **Pembaca** — EPUB 2/3 (Readium 3.4.0), PDF (`PdfRenderer`), TXT (paginasi internal), HTML lokal (WebView tanpa JS). Progres tersimpan otomatis, resume posisi, bookmark, daftar anotasi dengan lompat ke lokasi (EPUB).
* **Aktivitas & statistik** — sesi membaca nyata (waktu, durasi, progres sebelum→sesudah) dihitung dari data yang benar-benar dicatat.
* **Penerjemah** — halaman Translator + aksi "translate" dari ringkasan/teks terpilih memakai `ACTION_PROCESS_TEXT` dari aplikasi penerjemah yang terpasang. Bila tidak ada, dinyatakan jujur. Rincian: `docs/translation-integration.md`.
* **Personalisasi** — tema terang/gelap/sistem, empat preset aksen, ukuran teks baca 14–30 sp, layar tetap menyala, statistik bisa dimatikan.

Daftar lengkap beserta status jujur (Implemented / Not Tested / Planned): **`docs/feature-matrix.md`**.

## Cara build

Prasyarat: JDK 17, Android SDK dengan `compileSdk` 36, koneksi internet (unduh dependency).

```bash
python3 scripts/verify.py      # pemeriksaan statis (tanpa SDK)
./gradlew testDebugUnitTest    # unit test JVM
./gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
```

Atau lewat GitHub Actions: tab **Actions** → **Build** → **Run workflow** (juga berjalan otomatis pada setiap push/PR). Artifact `luread-debug-apk` berisi APK debug, dan `test-reports` berisi laporan test.

Catatan wrapper: `gradlew`, `gradlew.bat`, dan `gradle/wrapper/gradle-wrapper.jar` adalah **file resmi Gradle** (tag `v9.6.0`), bukan hasil pembuatan manual. Bila executable bit hilang setelah clone: `chmod +x gradlew`.

## Struktur

```
app/src/main/java/com/luread/app/
├── LureadApp.kt        Application + AppContainer (DI manual)
├── MainActivity.kt     Navigasi (Home/Discover/Library/Activity/Settings) + route
├── core/               brand, sistem desain, util
├── data/               network (Ktor), catalog, Room, DataStore, library, downloads
└── ui/                 layar + mesin reader (EPUB/PDF/TXT/HTML)
docs/                   riset, audit API, arsitektur, fitur, pengujian, penerjemahan
scripts/verify.py       pemeriksaan statis (dijalankan lokal & di CI)
.github/workflows/build.yml
```

## Batasan teknis (dibaca dulu)

1. **Kompilasi & unit test terverifikasi di CI, runtime belum.** Belum ada uji di perangkat/emulator; jalur Readium/PDF/WebView berstatus *Implemented, Not Tested on device* — kompilasi sukses bukan bukti perilaku.
2. **Sorting katalog tidak ada** — parameter `sort` Gutendex terbukti membalas 503, jadi kontrol sorting sengaja tidak dibuat.
3. **Filter `mime_type=text/plain` tidak ditawarkan** — terbukti 503 dari audit.
4. **PDF tidak mendukung seleksi teks** (keterbatasan `PdfRenderer` platform) → aksi terjemahan tidak ditampilkan di PDF.
5. **Tidak ada fitur kumpulan/koleksi kustom, TTS, kosakata, atau pengaturan lanjutan** — lihat matriks fitur; semuanya tercatat sebagai Planned, bukan disembunyikan.
6. **R8/ProGuard dinonaktifkan** pada konfigurasi release sampai konfigurasi obfuskasi untuk Readium/Ktor/serialization terverifikasi. CI hanya membangun debug.
7. **Logo resmi** sudah tersedia di `image/logo.png` dan diintegrasikan otomatis oleh task `integrateLogo` pada setiap `preBuild`: magic byte divalidasi (PNG/JPEG — file yang terpasang saat ini sebenarnya berisi data JPEG), disalin ke `res/drawable-nodpi/logo.*`, dan dipakai sebagai ikon launcher (`@drawable/logo`). Build sengaja **gagal dengan pesan jelas** bila file hilang atau formatnya tidak dikenali — tidak pernah memakai ikon buatan.
8. **`app/src/androidTest/` tidak dibuat** — tidak ada emulator di lingkungan ini, dan folder kosong hanya untuk memenuhi struktur dilarang.

## Privasi

Tanpa akun, tanpa API key, tanpa SDK pihak ketiga untuk analitik/iklan/crash. Jaringan hanya ke `gutendex.com` (katalog) dan URL `https://www.gutenberg.org/...` yang dikembalikan API (unduhan). Izin: `INTERNET`, `WAKE_LOCK` (agar layar tidak mati saat membaca).

## Dokumen

| Dokumen | Isi |
| --- | --- |
| `docs/research.md` | Versi dependency & cara verifikasinya, batasan lingkungan |
| `docs/gutendex-api-audit.md` | Audit API dari request nyata (fixture, status, temuan) |
| `docs/ui-research.md` | Keputusan desain & anti "AI slop" |
| `docs/architecture.md` | Arsitektur, aliran data, keputusan teknis |
| `docs/feature-matrix.md` | Status tiap fitur (jujur) |
| `docs/testing.md` | Apa yang diuji, apa yang tidak, cara menjalankan |
| `docs/translation-integration.md` | Integrasi penerjemah sistem & keterbatasannya |
