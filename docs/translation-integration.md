# Integrasi Penerjemahan — Luread

Tanggal: 12 Oktober 2026.

## 1. Ringkasan keputusan

Luread **tidak** membawa mesin terjemahan sendiri. Penerjemahan dilakukan lewat kontrak standar Android **`Intent.ACTION_PROCESS_TEXT`**: teks yang dipilih/diketik pengguna dikirim ke aplikasi penerjemah yang benar-benar terpasang di perangkat, dan hasilnya dibaca kembali dari `Intent.EXTRA_PROCESS_TEXT` pada activity result.

Alasan (bukan alasan kemalasan):

* Prompt melarang asumsi bahwa semua perangkat punya Google Translate, dan melarang pengiriman isi buku ke pihak ketiga tanpa persetujuan.
* Model ML Kit Translation butuh unduhan model puluhan MB per pasangan bahasa serta layanan Google — tidak bisa diverifikasi di lingkungan pengembangan ini, dan akan melanggar aturan "jangan mengklaim offline sebelum model benar-benar tersedia".
* Intent PROCESS_TEXT adalah API publik yang resmi didokumentasikan Android (developer.android.com, TextProcessing) dan tidak memerlukan API key.

## 2. API yang digunakan

| API | Berkas | Peran |
| --- | --- | --- |
| `Intent.ACTION_PROCESS_TEXT` + `type="text/plain"` | `ui/TranslatorScreen.kt`, `ui/BookDetailScreen.kt` (via navigasi), `ui/ReaderScreen.kt` | Mengirim teks ke aplikasi penerjemah |
| `Intent.EXTRA_PROCESS_TEXT` | `TranslatorScreen` (launcher result) | Membaca hasil terjemahan yang dikembalikan |
| `Intent.EXTRA_PROCESS_TEXT_READONLY = true` | `TranslatorScreen` | Memberi tahu aplikasi lain bahwa teks tidak boleh diubah di sumbernya |
| `Intent.createChooser(...)` | `TranslatorScreen` | Memilih aplikasi bila ada lebih dari satu |
| `PackageManager.queryIntentActivities` (+ `ResolveInfoFlags` untuk API ≥ 33) | `TranslatorScreen.countTranslateHandlers` | Menghitung handler yang tersedia sebelum menampilkan tombol |
| `ActivityResultContracts.StartActivityForResult` | `TranslatorScreen` | Menangkap hasil |
| `<queries>` di manifest | `AndroidManifest.xml` | Wajib sejak Android 11 (package visibility); tanpa ini `queryIntentActivities` selalu kosong |

Ketentuan Android yang dipatuhi:

* Tidak ada hardcode nama paket aplikasi penerjemah mana pun.
* Tidak ada asumsi "ada satu API penerjemah sistem universal" — memang tidak ada.
* Izin tambahan tidak diminta (intent ini tidak butuh izin).

## 3. Alur yang ada di kode (Implemented)

1. **Dari reader** (`ReaderScreen`): pengguna menyeleksi teks di EPUB (Readium `currentSelection()`), menekan tombol terjemah. Bila tidak ada teks terpilih → pesan jelas "select some text first", tanpa membuka aplikasi apa pun. Bila ada → intent PROCESS_TEXT dikirim. **PDF sengaja tidak menawarkan aksi seleksi** karena `PdfRenderer` platform tidak menyediakannya.
2. **Dari detail buku**: ringkasan buku bisa dikirim ke penerjemah (hanya ringkasan, **tidak pernah seluruh isi buku**).
3. **Halaman Translator** (`TranslatorScreen`): tempel/ketik teks → kirim ke aplikasi penerjemah → hasil (bila dikembalikan) ditampilkan dan bisa disalin. Bila `handlerCount == 0`, tombol kirim disembunyikan dan layar menyatakan dengan jujur bahwa tidak ada aplikasi penerjemah di perangkat.

## 4. Status pengujian (jujur)

| Item | Status |
| --- | --- |
| Versi Android yang dituju | minSdk 26, targetSdk 36 (kode memakai cabang API 33 untuk `ResolveInfoFlags`) |
| Aplikasi penerjemah pada lingkungan pengujian | **Tidak diketahui** — lingkungan pengembangan ini adalah PRoot tanpa GUI/perangkat Android |
| Uji `ACTION_PROCESS_TEXT` pada perangkat/emulator | **Belum dilakukan** |
| Uji alur reader → terjemah | **Belum dilakukan** |
| Uji halaman Translator | **Belum dilakukan** |
| Pengujian unit untuk kontrak intent | Tidak ditulis (butuh instrumentasi) |

Kesimpulan: integrasi berstatus **Implemented, Not Tested**. Klaim "pengguna dapat menerjemahkan teks dari reader" hanya berlaku **setelah** diuji pada perangkat yang benar-benar punya aplikasi penerjemah. Kode sudah menangani ketiadaan handler dengan pesan + fallback (salin teks).

## 5. Fallback bila tidak ada penerjemah

* Halaman Translator: menyembunyikan tombol kirim dan menampilkan `translator_no_app_title` / `translator_no_app_body`.
* Reader: teks yang gagal dikirim tetap bisa disalin lewat aksi sistem teks bawaan Android (menu konteks seleksi bawaan WebView/Readium).
* Tidak ada hasil terjemahan yang pernah ditampilkan tanpa benar-benar diterima dari aplikasi handler — tidak ada placeholder/terjemahan buatan.

## 6. Penerjemahan native (ML Kit) — status: Planned, sengaja tidak dipasang

Riset singkat: ML Kit Translation (mlkit.google.com) menawarkan `Translation.getClient(options)` + `downloadModelIfNeeded()`, dengan ukuran model puluhan MB per bahasa dan ketergantungan pada layanan Google Play services di banyak perangkat.

Keputusan: **belum diimplementasikan** karena:

1. Tidak bisa diverifikasi di lingkungan ini (tidak ada perangkat, tidak ada layanan Play).
2. Akan memunculkan status model (belum diunduh/sedang diunduh/siap/gagal) yang jujur pun belum bisa diuji.
3. Menambah dependensi native/layanan Google yang bertentangan dengan prinsip privasi minimum proyek saat ini.

Bila kelak ditambahkan, dokumen ini harus diperbarui dengan: versi artefak, daftar bahasa, ukuran model, status uji nyata.

## 7. Daftar periksa untuk pengujian berikutnya (perangkat nyata)

1. Pasang minimal satu aplikasi penerjemah (mis. Google Translate) → buka Translator → pastikan tombol muncul.
2. Pilih teks di EPUB → aksi terjemah muncul di menu konteks/toolbar → hasil kembali ke panel Luread.
3. Uji perangkat **tanpa** aplikasi penerjemah → pastikan pesan "no translation app" tampil, bukan crash.
4. Uji Android 11+ memastikan `<queries>` bekerja (handler terdeteksi).
5. Uji hasil dibatalkan pengguna (tombol kembali) → tidak menampilkan hasil palsu.
