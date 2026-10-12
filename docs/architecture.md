# Arsitektur Luread

Status dokumen: mencerminkan kode per 12 Oktober 2026. Klaim "Implemented" berarti kode ada; klaim " Tested" hanya untuk yang benar-benar dieksekusi (lihat `testing.md`).

## 1. Bentuk proyek

Satu modul Gradle (`:app`) dengan pembagian paket berlapis:

```
com.luread.app
├── LureadApp.kt          Application + AppContainer (DI manual)
├── MainActivity.kt       Activity, routing, NavigationBar
├── core
│   ├── branding/Logo.kt  Resolusi logo resmi bila tersedia (getIdentifier)
│   ├── design/           Theme.kt, Components.kt — sistem desain
│   └── util/             Format.kt (format angka/waktu), TxtDocument.kt (paginasi TXT)
├── data
│   ├── network/          Http.kt (Ktor), GutendexDtos.kt, GutendexApi.kt
│   ├── catalog/          Formats.kt (pemilihan MIME), CatalogRepository.kt
│   ├── db/               Entities.kt, Daos.kt, LureadDatabase.kt (Room)
│   ├── settings/         SettingsRepository.kt (Preferences DataStore)
│   ├── library/          LibraryRepository.kt (gabungan Room + logika)
│   └── downloads/        DownloadManager.kt (HttpURLConnection + validasi magic)
└── ui
    ├── LocalApp.kt, ViewModel.kt, CatalogUiState.kt
    ├── components/       BookItems.kt, Mappers.kt
    ├── reader/           ReaderViewModel, EpubContent, PdfReader, TextPager, HtmlReader,
    │                     SavedLocation (kode lokasi tahan-serialisasi), LocationCodec
    └── *Screen.kt        Home, Discover, BookDetail, Library, Activity, Settings, Translator, Reader
```

Keputusan: **satu modul** dengan pembagian paket, bukan multi-module. Untuk ukuran aplikasi ini, biaya konfigurasi multi-module (API surface, buildSrc, waktu konfigurasi) belum terbayar; pembagian paket menjaga batas agar tetap jelas dan mudah dipecah kelak.

## 2. Dependency injection manual

`AppContainer` (`LureadApp.kt`) dibuat sekali di `Application`:

```kotlin
class AppContainer(context: Context) {
    val httpClient: HttpClient          // Ktor + OkHttp + ContentNegotiation(Json)
    val catalog: GutendexApi
    val database: LureadDatabase        // Room
    val library: LibraryRepository
    val downloads: DownloadManager
    val settings: SettingsRepository    // DataStore
    val appScope: CoroutineScope        // SupervisorJob + Dispatchers.Default
    fun close()
}
```

* ViewModel dibuat lewat helper `appViewModel { container -> … }` (`ui/ViewModel.kt`) yang memakai `viewModelFactory` + `initializer` resmi — tanpa library DI tambahan.
* Alasan: tanpa Hilt/Koin, jumlah kelas kecil dan graph dangkal; menghindari dependency & KSP tambahan yang belum bisa diverifikasi di sini.

## 3. Aliran data

```
UI (Compose) ──collectAsStateWithLifecycle── ViewModel ── Repository ──┬─ Ktor → Gutendex
                                                                      ├─ Room  → SQLite
                                                                      ├─ DataStore → Preferences
                                                                      └─ DownloadManager → filesDir/books
```

* Semua aksi tulis berjalan `suspend` di `Dispatchers.IO` (repository) — tidak ada blocking di main thread.
* State layar memakai `StateFlow` + `collectAsStateWithLifecycle`.

## 4. Katalog (`data/catalog`)

| Komponen | Perilaku |
| --- | --- |
| `GutendexApi` | Membangun URL dari `CatalogQuery` (search, languages, topic, mime_type, page). `expectSuccess=false`; error dinormalkan ke `ApiError{Network,Timeout,Http,NotJson,InvalidUrl}`. |
| `CatalogRepository` | Retry: Network/Timeout 2× (backoff 400→900 ms), 503 1×, non-JSON tidak diulang. `next`/`previous` hanya diikuti bila host Gutendex (`isTrustedCatalogUrl`). |
| `Formats` | `normalizeMime`, `urlsByMime` (prioritas `charset=utf-8`), `bestReadable` (EPUB→HTML→TXT→PDF), `coverUrl`, `sniff` (magic `PK`/`%PDF`), `looksLikeEpub`, `extensionFor`. |

Batasan yang disengaja: tidak ada `sort` (parameter tak terverifikasi), tidak ada filter `mime_type=text/plain` (membalas 503).

## 5. Persistensi (`data/db`)

Room v1 (`LureadDatabase`, `exportSchema=true`, skema ditulis ke `app/schemas/`):

| Tabel | Isi | Indeks |
| --- | --- | --- |
| `books` | snapshot metadata katalog/impor + `localPath`, `downloadState`, `progress`, `locationJson`, `favorite`, `finished` | `addedAt`, `lastOpenedAt` |
| `sessions` | riwayat sesi baca (mulai, selesai, progres sebelum/sesudah) | `bookId`, `startedAt` |
| `annotations` | bookmark/sorotan/catatan; lokasi disimpan sebagai **JSON kecil milik Luread** (`SavedLocation`) | `bookId`, `createdAt` |

Keputusan penting: lokasi baca/anotasi **tidak** disimpan sebagai objek Readium langsung, agar perubahan skema navigator Readium tidak memaksa migrasi database. Codec: `ui/reader/SavedLocation.kt` (Serializable, diuji unit test).

Pengaturan: Preferences DataStore `luread_settings` (`theme_mode`, `accent`, `font_size_sp`, `keep_screen_on`, `track_reading_stats`).

## 6. Mesin pembaca (`ui/reader`)

| Format | Mesin | Catatan |
| --- | --- | --- |
| EPUB 2/3 | Readium 3.4.0 `ReflowableWebRendition` | `DefaultPublicationParser` + `AssetRetriever` lokal; `pdfFactory = null` (tanpa pdfium). |
| PDF | `androidx.graphics.pdf.PdfRenderer` + `HorizontalPager` | Halaman dirender ke bitmap; teks tidak diseleksi (PDF renderer platform tidak menyediakannya) → aksi penerjemahan tidak ditampilkan. |
| TXT | `core/util/TxtDocument.kt` + `TextPager.kt` | Dokumen dipotong per ±3500 karakter (cap 4 MB); UTF-8. |
| HTML lokal | `HtmlReader.kt` (WebView) | JavaScript dinonaktifkan, `file://` di dalam sandbox aplikasi. |

Progres disimpan per debounce 1,2 saat dan saat keluar reader (`ReaderViewModel.onLeave`) — termasuk penulisan sesi baca ke `sessions`.

## 7. Unduhan (`data/downloads/DownloadManager.kt`)

* `HttpURLConnection` di `Dispatchers.IO`, stream ke `filesDir/books/<id>.<ext>.part`.
* Setelah selesai: **validasi magic byte** (`PK` untuk EPUB/zip, `%PDF` untuk PDF) sebelum rename final → file rusak/HTML error tidak pernah ditandai `DONE`.
* Progress byte nyata (`contentLength` bila tersedia) dipublikasikan sebagai `StateFlow<Map<Int, Progress>>`.
* Batalkan menghapus file `.part`; `deleteFile` menghapus file nyata.

## 8. Penerjemahan (`ui/TranslatorScreen.kt` + aksi seleksi)

Menggunakan kontrak `Intent.ACTION_PROCESS_TEXT` / `EXTRA_PROCESS_TEXT` dengan `<queries>` di manifest. Detail, termasuk keterbatasan yang belum teruji: `docs/translation-integration.md`.

## 9. Keputusan privasi

* Tanpa akun, tanpa API key, tanpa analytics/iklan.
* Hanya dua host jaringan: `gutendex.com` (katalog) dan URL format hasil API (unduhan, yang semuanya `https://www.gutenberg.org/...` pada sampel).
* Tidak ada izin selain `INTERNET` dan `WAKE_LOCK`; tidak ada penyimpanan bersama (file di `filesDir`).
* URL dari API diverifikasi host-nya sebelum dijadikan target `next`/`previous`.

## 10. Build & CI

* AGP 9.4.1 dengan **Kotlin bawaan** (plugin `org.jetbrains.kotlin.android` tidak dipakai).
* Wrapper resmi Gradle 9.6.0 (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar` dari tag `v9.6.0` resmi).
* CI (`.github/workflows/build.yml`): static-checks (`scripts/verify.py`) → `./gradlew testDebugUnitTest` → `./gradlew assembleDebug` → upload APK + laporan test.
