# Feature Matrix — Luread

Tanggal: 13 Oktober 2026. Status mengikuti definisi di `docs/research.md`.
Paling penting: **"Implemented" ≠ "Tested"**. *Tested* = unit test lulus di CI (72/72, run `38356731098`) + pemeriksaan statis + **kompilasi sukses di CI**. Tidak ada fitur yang diuji pada perangkat; jalur runtime (Readium/PDF/WebView/unduhan) tetap *Implemented, Not Tested on device* meski kompilasi terverifikasi.

Legenda: `V` Verified (sumber/audit), `I` Implemented (kode ada), `IT` Implemented-Not-Tested, `P` Planned (belum dibuat), `—` tidak berlaku.

## 1. Katalog & jaringan

| Fitur prompt | Status | Bukti / catatan jujur |
| --- | --- | --- |
| Gutendex sebagai sumber | V | Audit penuh, `docs/gutendex-api-audit.md` |
| Open Library sebagai sumber | V | Uji curl penuh, `docs/book-apis-audit.md` §1; unduhan lewat `editions→ocaid→archive.org/metadata` |
| Internet Archive sebagai sumber | V | Uji curl penuh, §2 (`advancedsearch.php` + `metadata/{id}`); filter bahasa ISO 639-2 teruji |
| Wikisource Indonesia sebagai sumber | V | Uji curl penuh, §3 (`list=search`/`allpages`/`action=parse`; `prop=extracts` terbukti rusak di wiki ini) |
| Filter sumber (chip "Source") | I | `DiscoverScreen` + `DiscoverViewModel.setSource`; baris filter yang tidak didukung sumber aktif disembunyikan (bahasa di Wikisource; topik/format di luar Gutenberg) |
| Search (judul/penulis lewat `search`) | I | `DiscoverViewModel` debounce 400 ms |
| Pagination | I | `CatalogRepository.nextPage(cursor, query, source)` — kursor per sumber: URL tepercaya (Gutenberg), `page=N` (OL/IA), `search:offset`/`browse:judul` (Wikisource) |
| Infinite scroll terkendali | I | `DiscoverScreen`: load berikutnya saat item terakhir ≤ total−4 |
| Language filter | I | Gutenberg: 10 bahasa terverifikasi; Open Library & Internet Archive: kode ISO 639-2 (uji curl); Wikisource: disembunyikan (wiki khusus `id`) |
| Subject/topic filter | I | `children`, `poetry` — **hanya Gutenberg** (parameter OL/IA tidak didukung → baris disembunyikan) |
| Format filter | I | EPUB (`application/epub+zip`), HTML (`text/html`) **hanya Gutenberg**; `text/plain` **tidak** ditawarkan (503) |
| Sorting | — | Tidak ada di UI. Parameter `sort` terbukti 503 → kontrol sorting **tidak dibuat** agar tidak menampilkan pilihan palsu |
| Bookshelf filter | P | Field ada di API, parameter tidak diuji → tidak ditampilkan |
| Search result count | I | `count` dari API ditampilkan |
| Retry / error state | I | Network/Timeout 2×, 503 1×, non-JSON tidak diulang |
| Recent searches | P | |
| Search suggestions | P | |
| Related books | P | Butuh pemrosesan lokal metadata (bisa ditambah, belum ada) |
| Copy book link / share book | P | Tidak ada di `BookDetailScreen` |

## 2. Detail buku

| Fitur | Status | Catatan |
| --- | --- | --- |
| Cover, judul, penulis, bahasa, subjek, ringkasan, jumlah unduhan, hak cipta, daftar format | I | Semua dari metadata API; field kosong disembunyikan (bukan diisi teks palsu) |
| Read now / Download / Cancel download | I | Tombol sesuai status nyata |
| Add to library / Remove / Favorite | I | |
| Delete file lokal | I | Dengan konfirmasi |
| Translate summary | I | Lewat `ACTION_PROCESS_TEXT` |
| Translator (kontributor) | P | Field `translators` ada di DTO, belum ditampilkan |
| Add to collection / Share / Copy metadata / Open source page | P | |
| File size | — | Gutendex **tidak** menyediakan ukuran → tidak ditampilkan. Ukuran diisi hanya setelah unduhan berjalan (`totalBytes`) |
| Offline availability | I | Ditandai dari `localPath != null` |

## 3. Perpustakaan

| Fitur | Status |
| --- | --- |
| Daftar semua buku + filter All/Offline/Finished/Favorites | I |
| Favorite toggle per baris | I |
| Import file via SAF (`OpenDocument`), multi-format EPUB/PDF/TXT/HTML | I (satu per satu; multi-pick belum) |
| Validasi magic byte saat impor + deteksi format tak didukung | I |
| Duplikat saat impor | I (id berbasis hash path → upsert) |
| Progress per buku & terakhir dibuka | I |
| Remove dari perpustakaan | I |
| Grid/list toggle, sort, pencarian lokal, multi-select, batch action | P |
| Custom collections (buat, rename, pin, reorder, cover) | P |
| Archived books | P |
| Metadata editing buku lokal | P |
| Restore setelah dibuka kembali | I (data di Room, bukan memori) |

## 4. Reader

| Fitur | Status | Catatan |
| --- | --- | --- |
| EPUB 2/3 via Readium 3.4.0 | IT | Kode lengkap (`ReaderViewModel`, `EpubContent`); **kompilasi terverifikasi di CI**, runtime belum diuji di perangkat |
| PDF via `PdfRenderer` | IT | Render per halaman; **tanpa seleksi teks** (memang tidak ditampilkan aksi seleksi untuk PDF) |
| TXT (paginasi internal, cap 4 MB) | IT | Unit test untuk paginasinya *Tested* di CI |
| HTML lokal (WebView, JS mati) | IT | |
| Resume posisi baca | IT | `SavedLocation` JSON → `ReflowableWebGoLocation` |
| Auto-save progres (debounce 1,2 s + saat keluar) | IT | |
| Bookmark | IT | |
| Anotasi sheet (daftar, lompat, hapus) | IT | Lompat lokasi hanya EPUB |
| Highlight + catatan (model tersedia) | IT | Model & DAO ada; aksi buat highlight dari seleksi Readium belum dipasang di UI → **sebagian** |
| Progress bar & posisi | IT | |
| Keep screen on | IT | Lewat flag window + pengaturan |
| Kontrol auto-hide | I | Overlay disembunyikan; ketuk untuk menampilkan |
| TOC, search within book, page navigation EPUB, per-book settings, custom fonts, tap zones, arah baca | P | Butuh ekstensi Readium preferences/TOC yang belum dipasang |
| Error recovery file rusak | IT | Pesan error + state gagal (bukan crash); PDF invalid → pesan |

## 5. Kustomisasi

| Fitur | Status |
| --- | --- |
| Tema aplikasi Light/Dark/System | I (DataStore) |
| Aksen warna (4 preset) | I |
| Ukuran teks baca 14–30 sp | IT (diterapkan ke prefs Readium + pager teks) |
| Layar tetap menyala | I |
| Statistik aktif/nonaktif | I |
| Font family, line height, letter spacing, margin, tema sepia, warna kustom, tap zone, orientasi, brightness, per-book override, pencarian settings | P | 

Catatan: hanya pengaturan yang **benar-benar mengubah perilaku** yang ditampilkan (prompt: jangan membuat pengaturan yang hanya mengubah label).

## 6. Bahasa & penerjemahan

| Fitur | Status |
| --- | --- |
| Deteksi/filter bahasa dari metadata | I |
| ACTION_PROCESS_TEXT dari ringkasan buku & teks terpilih di EPUB | IT |
| Halaman Translator (ketik/tempel, kirim, salin hasil) | IT |
| Copy/Share sebagai fallback | I (copy); share penuh ke aplikasi terjemahan belum |
| Hasil terjemahan ditampilkan dalam panel Luread | IT | Hanya bila handler mengembalikan `EXTRA_PROCESS_TEXT`; bila tidak ada → pesan jujur |
| Pencarian kata di buku, vocabulary notebook, TTS, dictionary | P |
| ML Kit Translation native | P | Sengaja tidak dipasang (lihat `translation-integration.md`) |

## 7. Statistik & sesi

| Fitur | Status |
| --- | --- |
| Waktu membaca hari ini & total | I (dihitung dari tabel `sessions`) |
| Jumlah selesai | I |
| Riwayat sesi (buku, durasi, progres sebelum→sesudah) | I |
| Hapus riwayat | I |
| Kalender membaca, target harian/mingguan, pengingat, ekspor statistik | P |

Penting: waktu hanya dihitung ketika sesi benar-benar diamati aplikasi (masuk reader → keluar reader). Tidak ada timer latar yang mengarang angka.

## 8. Pengaturan global

| Kategori prompt | Yang tersedia |
| --- | --- |
| Appearance | tema, aksen |
| Reading | ukuran teks, keep-screen-on, statistik |
| Tools | buka penerjemah, hapus riwayat |
| About | penjelasan sumber data + versi |
| Motion, Library defaults, Network, Data backup/restore, Accessibility lanjutan, pencarian settings | **P** |

## 9. Privasi & keamanan

| Ketentuan | Status |
| --- | --- |
| Tanpa akun & API key | V/I |
| Izin minimal (`INTERNET`, `WAKE_LOCK`) | I |
| HTTPS saja, validasi host URL API | I (`isTrustedCatalogUrl`) |
| Tidak ada SDK analitik/iklan | V (daftar dependensi) |
| File pengguna di `filesDir` (privat) | I |
| Tidak mencetak isi buku ke log | I (dicek manual; tidak ada `Log` di jalur baca) |

## 10. Infrastruktur

| Item | Status |
| --- | --- |
| Gradle wrapper resmi 9.6.0 | V (file dari tag resmi; dicek `verify.py`) |
| Workflow CI (push/PR/manual), unit test, assembleDebug, upload artifact | V (hijau di run `38356731098`) |
| Unit test (parsing, format, paginasi TXT, codec lokasi, URL multi-sumber, pesan error, HTML strip) | V (72/72 lulus di CI) |
| AndroidTest / instrumentasi | **Tidak dibuat** (keputusan eksplisit: tidak ada emulator; folder sengaja tidak dibuat kosong) |
| Logo resmi | I | `image/logo.png` (isi JPEG 512×512) divalidasi magic byte oleh `:app:integrateLogo`, disalin ke `res/drawable-nodpi/logo.*`, dipakai sebagai `android:icon`. Build gagal bila file hilang/rusak |
| R8/ProGuard release | Dinonaktifkan sengaja (belum terverifikasi) |
