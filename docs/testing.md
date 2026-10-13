# Pengujian & Status Verifikasi — Luread

Tanggal: 13 Oktober 2026.

## 1. Ringkasan jujur

| Aktivitas | Status |
| --- | --- |
| Pemeriksaan statis lokal (`scripts/verify.py`) | **Dijalankan, lolos: 0 masalah, 0 peringatan** |
| Build lokal (toolchain PRoot, lihat §2) | **Berhasil** untuk `testDebugUnitTest` (juga `compileDebugKotlin`); `assembleDebug` dihentikan di tengah (proses dex berat di RAM sempit) — APK dibangun CI |
| Kompilasi Kotlin / `assembleDebug` | **Lulus di CI** (run `38326003012`, commit `1dcdba8`) |
| Unit test (`./gradlew testDebugUnitTest`) | **Lulus lokal: 72/72** (CI sebelumnya 35/35, run `38326003012`) |
| Lint Android (`./gradlew lint`) | **Tidak dijalankan** (belum ditambahkan ke CI) |
| Uji di perangkat/emulator | **Tidak ada** perangkat/emulator di lingkungan ini |
| GitHub Actions | **Hijau** — static-checks + Unit tests sukses; artifact `luread-debug-apk` (~33 MB) & `test-reports` terbit |

Konsekuensi: kompilasi, resource, Room KSP, Readium API, dan unit test terverifikasi di CI. Klaim "aplikasi berjalan dengan benar di perangkat" tetap tidak bisa dibuat.

## 2. Build lokal: dimungkinkan (diperbarui 13 Okt 2026)

Rintisan awal (JDK tak ada, `aapt2` Maven ELF x86-64 di host aarch64) akhirnya
diselesaikan — build lokal kini berjalan di lingkungan PRoot ini:

1. `apk add openjdk17-jdk` (JDK 17 native aarch64) + `qemu-x86_64` + `gcc`.
2. Android SDK manual di `/tmp/toolchain/sdk`: cmdline-tools resmi, lalu
   `platform-37.0_r02.zip` dan `build-tools_r36_linux.zip` diekstrak ke
   `platforms/android-37.0` dan `build-tools/android-16` (task SDK install
   otomatis AGP ikut bekerja setelah keduanya ada).
3. Loader glibc x86-64 Debian diekstrak ke `/tmp/toolchain/x86` untuk
   menjalankan biner SDK/AGP via `qemu-x86_64`.
4. AGP menolak wrapper non-ELF ("does not point to an AAPT2 executable") →
   dibuat wrapper ELF kecil (`/tmp/toolchain/bin/aapt2`, program C yang
   `exec` qemu) dan ditunjuk lewat `android.aapt2FromMavenOverride`.
5. Heap dibatasi (`-Xmx900m`, metaspace 300m, worker 1) — RAM host ~7,6 GB
   dengan ~1 GB bebas; heap besar mematikan daemon diam-diam.

Hasil: `./gradlew :app:testDebugUnitTest` dan `:app:compileDebugKotlin`
berjalan lokal (build pertama ~16 menit, inkremental ~2–7 menit).
`assembleDebug` pernah dijalankan lokal tetapi dihentikan di fase
`mergeExtDexDebug` (dexing sangat rakus RAM di host ini) → produksi APK
diserahkan ke CI. Catatan: command panjang dijalankan background
(`setsid … &`) lalu di-poll; `pkill -f GradleDaemon` membunuh shell sendiri —
pakai pola `Gradle[D]aemon` bila perlu.

## 3. Pemeriksaan statis lokal (dijalankan)

`python3 scripts/verify.py` memeriksa:

1. `gradle/libs.versions.toml` valid (tomllib) dan setiap aksesur `libs.*` yang dirujuk skrip Gradle ada.
2. `AndroidManifest.xml` & file res valid XML; `android:icon="@drawable/logo"` (wajib; logo divalidasi magic byte PNG/JPEG); `INTERNET` + `WAKE_LOCK`; `<queries>` untuk `ACTION_PROCESS_TEXT`.
3. Setiap `R.string.X` yang dipakai kode ada di `strings.xml`, dan sebaliknya (peringatan bila ada yang tidak terpakai).
4. Setiap file Kotlin: package cocok dengan direktori; kurung `{} () []` seimbang setelah komentar/string dibuang (scanner satu-pass, menghormati string yang memuat `//`).
5. Tidak ada pola sintaks rusak `.androidx.`.
6. Workflow: bebas tab, tag aksi berbentuk `owner/repo[@subpath]@ref`, memakai `./gradlew`, `testDebugUnitTest`, `assembleDebug`.
7. Wrapper: `gradlew` (skrip shell resmi + bit eksekusi), `gradlew.bat`, `gradle-wrapper.jar` berisi `GradleWrapperMain.class`, `gradle-wrapper.properties` menunjuk Gradle 9.6.0 sesuai toml.

Hasil terakhir: **Lolos: 0 masalah, 0 peringatan.**

> Pemeriksaan ini **bukan** pengganti kompilasi (kompilasi dijalankan CI); ia menangkap kelas kesalahan tertentu lebih cepat.

## 4. Unit test yang ditulis — 72/72 lulus (lokal; angka CI diperbarui setelah run terakhir)

| Berkas | Yang diuji |
| --- | --- |
| `data/catalog/FormatsTest.kt` | Normalisasi MIME (parameter charset), pemilihan URL prioritas utf-8, prioritas format EPUB→HTML→TXT→PDF, cover, ekstensi, magic byte zip/PDF |
| `core/util/TxtDocumentTest.kt` | Paginasi 3500 karakter, pemotongan di batas baris, cap 4 MB (`readCapped`), penanganan CRLF |
| `core/util/FormatTest.kt` | Format persen, byte, durasi, durasi detik, tanggal |
| `core/util/HtmlStripTest.kt` | **Baru** — HTML→teks: blok, script/style, br/heading, entitas (termasuk numerik), perapian spasi |
| `ui/reader/SavedLocationTest.kt` | Encode/decode lokasi baca (JSON) — dipulihkan persis, aman bila field hilang |
| `data/network/GutendexApiUrlTest.kt` | Pembangunan URL katalog (search/language/topic/mime/page) dan aturan URL tepercaya (`next`/`previous`) |
| `data/network/OpenLibraryApiUrlTest.kt` | **Baru** — q default `subject:fiction`, `sort=editions`, bahasa ISO, kursor halaman, mapper doc→item, deskripsi string/objek |
| `data/network/ArchiveApiUrlTest.kt` | **Baru** — query dasar+`subject:fiction`/`title:(…)`, escape frasa, `fl[]`, metadata URL, mapper doc→item, normalisasi string/array |
| `data/network/WikisourceApiUrlTest.kt` | **Baru** — `list=search`/`allpages`/`sroffset`/`apcontinue`, `action=parse`, `wrapDocument`, `escapeHtml` |
| `data/catalog/BookSourceTest.kt` | **Baru** — prefix kunci per sumber, fallback kunci asing, `remoteIdOf`, jaminan tanpa slash |
| `data/catalog/ArchiveFormatsTest.kt` | **Baru** — preferensi EPUB/PDF/teks, PDF turunan, map kosong, encode URL unduhan |
| `data/catalog/CatalogItemTest.kt` | **Baru** — pemetaan `fromGutendex` (field + cover), cover dari field langsung |
| `ui/CatalogErrorTest.kt` | Pemetaan `ApiError` → pesan pengguna (termasuk `NotFound` baru) |

Yang sengaja **tidak** ditulis: test Room/DAO & DataStore (butuh instrumentasi atau Robolectric yang tidak bisa diverifikasi di sini), test Compose UI, test Readium.

## 5. CI (`.github/workflows/build.yml`)

Alur:

1. `static-checks` — jalankan `scripts/verify.py` (Python bawaan runner).
2. `build` — `actions/setup-java@v6.0.1` (Temurin 17) → `gradle/actions/setup-gradle@v6.4.0` (cache) → `chmod +x gradlew` → `./gradlew testDebugUnitTest` → `./gradlew assembleDebug` → upload `build-reports/` (selalu) dan `app/build/outputs/apk/debug/*.apk`.

Pemicu: push (semua cabang), pull request, `workflow_dispatch`. Permission `contents: read` saja. Tidak ada secret.

Catatan: file wrapper adalah **file resmi Gradle** dari tag `v9.6.0` (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`), bukan file yang dibuat manual.

Riwayat perbaikan CI (3 run pertama gagal, keempat hijau):

1. `checkDebugAarMetadata`: Readium 3.4.0 & navigation-compose 2.10.2 menuntut **compileSdk ≥ 37** → `compileSdk = 37` + **core library desugaring** (`desugar_jdk_libs 2.1.5`).
2. `sdkmanager "platforms;android-37"` tidak dikenali runner → dihapus, andalkan auto-download AGP.
3. Kesalahan kompilasi Kotlin: `CatalogQuery` salah paket, enum `Formats` membayangi konstanta MIME, `ContentType.Json` tidak ada di Ktor 3 (→ `ContentType.Application.Json`), properti timeout Ktor 3.6 (`requestTimeoutMillis`), `followRedirects` internal di OkHttp 5, `textZoom` ada di `settings`, ikon Compose butuh import.
4. 4 unit test gagal (asumsi bahasa Indonesia setelah i18n + pembulatan byte) → diperbaiki; run ke-4 **hijau penuh**.

## 6. Yang belum diuji (daftar terbuka)

* Perilaku runtime: membuka EPUB sungguhan, render PDF, WebView HTML, unduhan, impor SAF — **belum diuji di perangkat/emulator mana pun**.
* `ACTION_PROCESS_TEXT` pada perangkat dengan/tanpa aplikasi penerjemah (lihat `translation-integration.md`).
* Dark mode, font scaling ekstrem, tablet, TalkBack, rotasi, proses dibunuh sistem.
* `lint` Android.

Kompilasi seluruh kode (termasuk jalur Readium & Room KSP) sudah **terverifikasi di CI**; yang tersisa adalah pengujian perilaku.

## 7. Cara menjalankan sendiri

```bash
# di mesin dengan JDK 17 + Android SDK (compileSdk 37, desugaring)
python3 scripts/verify.py      # pemeriksaan statis
./gradlew testDebugUnitTest    # unit test
./gradlew assembleDebug        # APK -> app/build/outputs/apk/debug/app-debug.apk
```

Atau tekan **Run workflow** pada tab Actions repositori (tanpa setup lokal).
