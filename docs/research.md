# Riset & Verifikasi — Luread

Tanggal pemeriksaan: **12 Oktober 2026**.
Lingkungan pengembangan: Alpine aarch64 (PRoot di Android), Python 3.14.8, Node 24.18.1, git 2.54.0. **Tidak ada JDK, Android SDK, Gradle, atau Kotlin compiler di mesin ini.**

Status yang dipakai di seluruh dokumen (mengikuti prompt bagian 23):

| Status | Arti |
| --- | --- |
| **Verified** | Diverifikasi terhadap sumber primer / registry / pengujian yang benar-benar dijalankan. |
| **Implemented** | Kode ada dan tersambung; **belum tentu** pernah dieksekusi. |
| **Tested** | Pengujian benar-benar dijalankan dan hasilnya dicatat. |
| **Planned** | Belum dibuat. |

Satu status tidak otomatis memenuhi status lainnya.

## 1. Batasan lingkungan yang memengaruhi keputusan

| Fakta | Bukti | Konsekuensi |
| --- | --- | --- |
| Tidak ada `java`/`javac`/`gradle`/`ANDROID_HOME` | `which`/`command -v` kosong di shell | Build APK & unit test **hanya bisa di CI** (GitHub Actions). Verifikasi lokal dibatasi pemeriksaan statis. |
| `aapt2` dari Maven hanya untuk Linux x86-64 | `com.android.tools.build:aapt2:9.4.1-15978811` diunduh → `file` = ELF 64-bit x86-64; dieksekusi → `cannot execute: required file not found` (exit 127) | Host aarch64 tanpa qemu/binfmt: tool Android native **tidak bisa dijalankan sama sekali**. |
| PyYAML tidak tersedia | `import yaml` → ModuleNotFoundError | YAML workflow divalidasi dengan parser subset buatan sendiri (`scripts/verify.py`). |
| Nama file `gradlew` dari distro resmi diunduh | `raw.githubusercontent.com/gradle/gradle/v9.6.0/{gradlew,gradlew.bat,gradle/wrapper/gradle-wrapper.jar}` | Wrapper adalah **file resmi Gradle**, bukan hasil pembuatan manual. Jumlah unduhan: 3 file teks/jar kecil. |

## 2. Versi dependency (semua **Verified** terhadap registry)

Sumber: `maven-metadata.xml` / POM di `dl.google.com/dl/android/maven2` dan `repo1.maven.org/maven2` (dicentang ulang pada 2026-10-12, semua HTTP 200).

| Artefak | Versi | Catatan verifikasi |
| --- | --- | --- |
| `com.android.tools.build:gradle` (AGP) | 9.4.1 | metadata ada |
| Gradle (wrapper) | 9.6.0 | `gradle-wrapper.properties` menunjuk `gradle-9.6.0-bin.zip`; file wrapper dari tag `v9.6.0` |
| Kotlin / KGP / plugin Compose & Serialization | 2.4.21 | plugin marker `org.jetbrains.kotlin.plugin.compose` 200 |
| KSP | 2.3.12 | marker plugin di `plugins.gradle.org` → `latest=2.3.12` (redirect 303 mengikuti `-L`) |
| `androidx.compose:compose-bom` | 2026.06.01 | POM ditelusuri: pin **Compose 1.11.4** (ui/foundation/runtime), **material3 1.4.0**, **material-icons-extended 1.7.8** |
| `androidx.core:core-ktx` | 1.19.0 | metadata ada |
| `androidx.activity:activity-compose` | 1.13.0 | metadata ada |
| `androidx.lifecycle:*` (runtime-compose, viewmodel-compose, runtime-ktx) | 2.11.0 | metadata ada |
| `androidx.navigation:navigation-compose` | 2.10.2 | metadata ada |
| `androidx.room:*` (runtime, ktx, compiler) | 2.8.5 | metadata ada |
| `androidx.datastore:datastore-preferences` | 1.2.1 | metadata ada |
| `io.ktor:*` (core, okhttp, content-negotiation, serialization-kotlinx-json) | 3.6.0 | metadata ada |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.11.0 | metadata ada |
| `org.jetbrains.kotlinx:kotlinx-coroutines-{android,test}` | 1.11.0 | metadata ada |
| `io.coil-kt:coil-compose` | 2.7.0 | metadata ada |
| `org.readium.kotlin-toolkit:{readium-shared,readium-streamer,readium-navigator,readium-navigator-web-reflowable}` | 3.4.0 | keempat artefak 200 |
| `junit:junit` | 4.13.2 | metadata ada |

Keputusan pemakaian versi BOM: **Compose BOM 2026.06.01 dipilih secara sengaja**. BOM lebih baru (2026.08/2026.09) memuat Compose 1.12.x yang mensyaratkan `compileSdk` 37, sedangkan repo SDK Google saat dicek tidak menyediakan `androidx.test:master`/platform-37 yang bisa dipakai di sini → dipakai Compose 1.11.4 + compileSdk 36.

## 3. AGP 9 dan Kotlin bawaan (**Verified**)

AGP 9 menyertakan dukungan Kotlin bawaan. Konsekuensi langsung yang dipatuhi di `build.gradle.kts`:

* Plugin `org.jetbrains.kotlin.android` **tidak dipakai dan tidak boleh dipakai** (akan bentrok/ditolak).
* Plugin yang dipakai hanya: `com.android.application`, `org.jetbrains.kotlin.plugin.compose`, `org.jetbrains.kotlin.plugin.serialization`, `com.google.devtools.ksp`.
* Sumber: halaman dokumen AGP yang diunduh saat audit (`/tmp/audit/builtin-kotlin.html`, `/tmp/audit/kotlin-support.html`, `/tmp/audit/compose-setup.html`) yang menyatakan Compose compiler plugin dipasang lewat `org.jetbrains.kotlin.plugin.compose` pada proyek AGP 9.

## 4. GitHub Actions tags (**Verified** via GitHub API `releases/latest`, 2026-10-12)

| Repo | Tag rilis terbaru | Dipakai di workflow |
| --- | --- | --- |
| `actions/checkout` | `v7.0.1` | ya |
| `actions/setup-java` | `v6.0.1` | ya |
| `actions/upload-artifact` | `v7.0.2` | ya |
| `gradle/actions` | `v6.4.0` | ya (`gradle/actions/setup-gradle@v6.4.0`) |

## 5. Readium Kotlin Toolkit 3.4.0 (**Verified** dari source tag)

Sumber primer: source tree tag `3.4.0` yang diambil saat audit (`/tmp/audit/rsrc/*.kt`, `/tmp/audit/rdoc/*.md`). API yang benar-benar dipakai kode Luread dan terkonfirmasi ada di source:

| API Readium | Dipakai di |
| --- | --- |
| `File(path).toUrl(isDirectory = false)` | `ReaderViewModel` |
| `DefaultPublicationParser(context, httpClient, assetRetriever, pdfFactory = null)` | `ReaderViewModel` |
| `assetRetriever.retrieve(file: File)` | `ReaderViewModel` |
| `ReflowableWebRenditionFactory(application, configuration)` (nullable) | `ReaderViewModel` |
| `factory.createRenditionState(initialPreferences, initialLocation)` (suspend, `Try`) | `ReaderViewModel` |
| `ReflowableWebRendition(state, inputListener, hyperlinkListener, textSelectionActionModeCallback)` | `EpubContent` |
| `state.controller?.location` (Compose `State`), `goTo`, `currentSelection()`, `preferences` | `EpubContent` |
| `ReflowableWebGoLocation(href, progression: Progression?)`, `Progression(double)`, `Position(value)` | `LocationCodec` |
| `location.totalProgression`, `location.href`, `location.position`, `.toLocator()` | `ReaderViewModel`, `LocationCodec` |
| `ReflowableWebPreferences` + `copy(scroll, fontSize: Double, overridePublisherColors)` | `ReaderViewModel` |

Catatan jujur: pemakaian API ini **belum pernah dikompilasi** (lihat `docs/testing.md`). Risiko kompilasi tertinggi ada di `ReaderViewModel`, `EpubContent`, dan `PdfReader`.

## 6. Keputusan teknologi yang dipilih (dan yang ditolak)

| Keputusan | Alasan | Sumber |
| --- | --- | --- |
| Katalog = Gutendex resmi | Prompt; API diverifikasi penuh (lihat `gutendex-api-audit.md`) | `https://gutendex.com/books/` |
| HTTP = Ktor + OkHttp engine | Sinkron async, plugin `ContentNegotiation` + kotlinx.serialization | repo1.maven.org |
| Persistensi = Room (buku, sesi, anotasi) + Preferences DataStore (pengaturan) | Prompt bagian 18 | developer.android.com |
| EPUB 2/3 = Readium reflowable web rendition | Satu-satunya opsi open-source yang mapan untuk Kotlin Android | readium.org docs + source 3.4.0 |
| PDF = `androidx.graphics.pdf` (`PdfRenderer`) bawaan platform | Tanpa native library tambahan (pdfium) yang tidak bisa diverifikasi ukuran/lisensinya di sini | developer.android.com |
| TXT/HTML = renderer sendiri | Kecil, kontrol penuh, tanpa dependensi | — |
| Penerjemahan = `Intent.ACTION_PROCESS_TEXT` + `<queries>` | Prompt bagian 26; tanpa API key, tanpa teks buku dikirim ke server oleh Luread | developer.android.com (TextProcessing) |
| **ML Kit Translation ditolak** | Butuh unduhan model per bahasa (puluhan MB), layanan Google, dan tidak bisa diverifikasi di sini → akan melanggar aturan "jangan klaim offline sebelum model tersedia". Status dicatat **Planned**. | mlkit.google.com |
| Tanpa analytics/iklan/crash SDK | Prompt bagian 19 (privasi) | — |
| Release `isMinifyEnabled = false` | Konfigurasi R8 untuk Readium/Ktor/serialization belum bisa diverifikasi → jangan menutupi risiko dengan obfuskasi yang belum diuji | keputusan sendiri, dicatat di `app/build.gradle.kts` |

## 7. Sumber yang diakses

* `https://gutendex.com/books/` (dan varian query) — 12 Okt 2026, fixture di `/tmp/audit/gutendex/`.
* `https://github.com/garethbjohnson/gutendex` — repo referensi API (dokumentasi perilaku).
* `https://dl.google.com/dl/android/maven2/` — metadata/POM AGP, AndroidX, Compose BOM.
* `https://repo1.maven.org/maven2/` — metadata Ktor, kotlinx, Coil, Readium, JUnit.
* `https://plugins.gradle.org/m2/` — marker plugin Kotlin Compose & KSP.
* `https://api.github.com/repos/{actions/checkout,actions/setup-java,actions/upload-artifact,gradle/actions}/releases/latest`.
* `https://raw.githubusercontent.com/gradle/gradle/v9.6.0/...` — file wrapper resmi.
* `https://github.com/readium/kotlin-toolkit/tree/3.4.0` — source API Readium.
* Dokumen Android Developers & dokumen Compose yang diunduh saat audit (tersimpan di `/tmp/audit/`).

## 8. Yang **tidak** bisa diverifikasi di lingkungan ini

* Kompilasi Kotlin, eksekusi unit test, dan pembuatan APK (butuh JDK + SDK) → hanya CI.
* Perilaku runtime Readium/PdfRenderer/WebView di perangkat nyata.
* Aksi `ACTION_PROCESS_TEXT` pada perangkat (butuh perangkat/emulator + aplikasi penerjemah).
* Lint Android (`./gradlew lint`) — tidak dijalankan di CI saat ini agar runtime workflow tetap wajar.
