# Pengujian & Status Verifikasi — Luread

Tanggal: 13 Oktober 2026.

## 1. Ringkasan jujur

| Aktivitas | Status |
| --- | --- |
| Pemeriksaan statis lokal (`scripts/verify.py`) | **Dijalankan, lolos: 0 masalah, 0 peringatan** |
| Kompilasi Kotlin / `assembleDebug` | **Lulus di CI** (run `38326003012`, commit `1dcdba8`) |
| Unit test (`./gradlew testDebugUnitTest`) | **Lulus di CI: 35/35** |
| Lint Android (`./gradlew lint`) | **Tidak dijalankan** (belum ditambahkan ke CI) |
| Uji di perangkat/emulator | **Tidak ada** perangkat/emulator di lingkungan ini |
| GitHub Actions | **Hijau** — static-checks + Unit tests sukses; artifact `luread-debug-apk` (~33 MB) & `test-reports` terbit |

Konsekuensi: kompilasi, resource, Room KSP, Readium API, dan unit test terverifikasi di CI. Klaim "aplikasi berjalan dengan benar di perangkat" tetap tidak bisa dibuat.

## 2. Mengapa build lokal tidak mungkin (fakta, bukan asumsi)

1. Tidak ditemukan `java`, `javac`, `gradle`, `kotlinc`, atau `ANDROID_HOME` di host (Alpine aarch64/PRoot).
2. Upaya memakai `aapt2` dari Maven: artefak `com.android.tools.build:aapt2:9.4.1-15978811` ternyata ELF **x86-64**; host adalah **aarch64** tanpa qemu/binfmt → eksekusi berakhir `cannot execute: required file not found` (exit 127).
3. Kesimpulan: semua aktivitas butuh JDK/SDK/AVD hanya bisa dilakukan di CI atau mesin pengembang normal.

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

## 4. Unit test yang ditulis — semua lulus di CI (35/35)

| Berkas | Yang diuji |
| --- | --- |
| `data/catalog/FormatsTest.kt` | Normalisasi MIME (parameter charset), pemilihan URL prioritas utf-8, prioritas format EPUB→HTML→TXT→PDF, cover, ekstensi, magic byte zip/PDF |
| `core/util/TxtDocumentTest.kt` | Paginasi 3500 karakter, pemotongan di batas baris, cap 4 MB (`readCapped`), penanganan CRLF |
| `core/util/FormatTest.kt` | Format persen, byte, durasi, durasi detik, tanggal |
| `ui/reader/SavedLocationTest.kt` | Encode/decode lokasi baca (JSON) — dipulihkan persis, aman bila field hilang |
| `data/network/GutendexApiUrlTest.kt` | Pembangunan URL katalog (search/language/topic/mime/page) dan aturan URL tepercaya (`next`/`previous`) |
| `ui/CatalogErrorTest.kt` | Pemetaan `ApiError` → pesan pengguna |

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
