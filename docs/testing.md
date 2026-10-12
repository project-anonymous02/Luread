# Pengujian & Status Verifikasi — Luread

Tanggal: 12 Oktober 2026.

## 1. Ringkasan jujur

| Aktivitas | Status |
| --- | --- |
| Pemeriksaan statis lokal (`scripts/verify.py`) | **Dijalankan, lolos: 0 masalah, 0 peringatan** |
| Kompilasi Kotlin / `assembleDebug` | **Tidak bisa dijalankan di lingkungan ini** (tanpa JDK & Android SDK) |
| Unit test (`./gradlew testDebugUnitTest`) | **Belum dijalankan** — targetnya adalah CI |
| Lint Android (`./gradlew lint`) | **Tidak dijalankan** (tidak ada SDK lokal; belum ditambahkan ke CI) |
| Uji di perangkat/emulator | **Tidak ada** perangkat/emulator di lingkungan ini |
| GitHub Actions pertama | **Belum dijalankan** dari sesi pengembangan ini |

Konsekuensi: tidak ada klaim "aplikasi sudah jalan" yang bisa dibuat saat ini. Kode berstatus **Implemented, Not Tested** kecuali yang tertulis lain.

## 2. Mengapa build lokal tidak mungkin (fakta, bukan asumsi)

1. Tidak ditemukan `java`, `javac`, `gradle`, `kotlinc`, atau `ANDROID_HOME` di host (Alpine aarch64/PRoot).
2. Upaya memakai `aapt2` dari Maven: artefak `com.android.tools.build:aapt2:9.4.1-15978811` ternyata ELF **x86-64**; host adalah **aarch64** tanpa qemu/binfmt → eksekusi berakhir `cannot execute: required file not found` (exit 127).
3. Kesimpulan: semua aktivitas butuh JDK/SDK/AVD hanya bisa dilakukan di CI atau mesin pengembang normal.

## 3. Pemeriksaan statis lokal (dijalankan)

`python3 scripts/verify.py` memeriksa:

1. `gradle/libs.versions.toml` valid (tomllib) dan setiap aksesur `libs.*` yang dirujuk skrip Gradle ada.
2. `AndroidManifest.xml` & file res valid XML; tanpa `android:icon` (logo resmi belum ada); `INTERNET` + `WAKE_LOCK`; `<queries>` untuk `ACTION_PROCESS_TEXT`.
3. Setiap `R.string.X` yang dipakai kode ada di `strings.xml`, dan sebaliknya (peringatan bila ada yang tidak terpakai).
4. Setiap file Kotlin: package cocok dengan direktori; kurung `{} () []` seimbang setelah komentar/string dibuang (scanner satu-pass, menghormati string yang memuat `//`).
5. Tidak ada pola sintaks rusak `.androidx.`.
6. Workflow: bebas tab, tag aksi berbentuk `owner/repo[@subpath]@ref`, memakai `./gradlew`, `testDebugUnitTest`, `assembleDebug`.
7. Wrapper: `gradlew` (skrip shell resmi + bit eksekusi), `gradlew.bat`, `gradle-wrapper.jar` berisi `GradleWrapperMain.class`, `gradle-wrapper.properties` menunjuk Gradle 9.6.0 sesuai toml.

Hasil terakhir: **Lolos: 0 masalah, 0 peringatan.**

> Pemeriksaan ini **bukan** pengganti kompilasi. Ia hanya menangkap kelas kesalahan tertentu (resource hilang, struktur file rusak, referensi versi salah).

## 4. Unit test yang ditulis (target: CI)

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

## 6. Yang belum diuji (daftar terbuka)

* Kompilasi seluruh kode, khususnya jalur Readium (`ReaderViewModel`, `EpubContent`) dan Room (KSP).
* Perilaku runtime: membuka EPUB sungguhan, render PDF, WebView HTML, unduhan, impor SAF.
* `ACTION_PROCESS_TEXT` pada perangkat dengan/tanpa aplikasi penerjemah (lihat `translation-integration.md`).
* Dark mode, font scaling ekstrem, tablet, TalkBack, rotasi, proses dibunuh sistem.
* `lint` Android.

Bila CI gagal: perbaiki berdasarkan log CI (artifact `test-reports` + console), jalankan ulang. Jangan menganggap APK ada sebelum log menunjukkan `assembleDebug` sukses dan artifact terunduh.

## 7. Cara menjalankan sendiri

```bash
# di mesin dengan JDK 17 + Android SDK (compileSdk 36)
python3 scripts/verify.py      # pemeriksaan statis
./gradlew testDebugUnitTest    # unit test
./gradlew assembleDebug        # APK -> app/build/outputs/apk/debug/app-debug.apk
```

Atau tekan **Run workflow** pada tab Actions repositori (tanpa setup lokal).
