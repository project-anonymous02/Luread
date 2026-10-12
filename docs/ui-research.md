# Riset UI/UX — Luread

Tanggal: 12 Oktober 2026.

**Kejujuran metode.** Riset UI di proyek ini berbasis tiga sumber: (a) ketentuan desain di dalam prompt itu sendiri (bagian 5–6: anti "AI slop", hierarki visual, animasi yang tidak mengganggu membaca), (b) spesifikasi komponen Material 3 yang dipakai apa adanya di `core/design/*`, dan (c) bentuk data katalog Gutendex yang sebenarnya (lihat `gutendex-api-audit.md`). **Perbandingan langsung terhadap aplikasi komersial (Kindle, ReadEra, dst.) tidak dilakukan** — tidak ada emulator/perangkat dan tidak ada tangkapan layar aplikasi pihak ketiga di lingkungan ini. Bagian yang tidak bisa dibuktikan ditandai demikian.

## 1. Prinsip yang dipatuhi

1. **Tidak ada elemen dekoratif yang berpura-pura berfungsi.** Setiap tombol yang tampil punya handler; bila data tidak tersedia (mis. ukuran file dari katalog, sorting API), kontrolnya **tidak ditampilkan** alih-alih tampil tapi mati.
2. **Warna dan elevasi membawa makna, bukan hiasan.** Aksen tunggal (`AccentPreset`) dipakai untuk tindakan utama; status (unduhan, selesai) ditandai juga lewat ikon/teks, bukan hanya warna (kriteria aksesibilitas "non-color status indicators").
3. **Membaca adalah tugas utama.** Overlay reader auto-hide, kontrol minimum, `keepScreenOn` default aktif, dan animasi hanya fade pendek.
4. **State kosong selalu menjelaskan langkah berikutnya.** Tidak ada layar dengan spinner tanpa akhir: setiap `Loading` punya jalur `Error + Retry` dan `Empty` dengan CTA.

## 2. Sistem desain yang dibangun (Implemented)

Semua layar memakai satu sistem di `core/design/`:

| Elemen | Berkas | Keputusan |
| --- | --- | --- |
| `LureadTheme(mode, accent, extras, content)` | `Theme.kt` | Dinamis lewat `lightColorScheme`/`darkColorScheme` yang dibangkitkan dari `AccentPreset`; menghormati `ThemeMode.SYSTEM` dari DataStore. |
| `AccentPreset` (Clay, Pine, Indigo, Plum) | `Theme.kt` | Palet hasil kurasi manual (nilai RGB ditulis tangan), bukan warna default Material yang terasa "template". |
| `LocalLureadExtras` (`coverAspectRatio`, `gridColumns`) | `Theme.kt` | Titik ekstensibilitas kepadatan tanpa mengubah signature komponen. |
| `LureadSpace` (Xs…SectionGap) | `Theme.kt` | Skala spasi tunggal — mencegah jarak acak antarlayar. |
| `SectionHeader`, `BookCover`, `BookRowItem`, `BookGridItem`, `ProgressLine`, `StateMessage`, `LoadingState`, `SkeletonRows`, `PrimaryActionButton`, `MetaRow`, `SettingsRow`, `MotionFade`, `VSpace` | `core/design/Components.kt`, `ui/components/*` | Komponen dipakai ulang; kartu buku sama di Home/Discover/Library. |
| `BookCover` | `Components.kt` | Rasio 0.68 (rasio sampul buku umum) + **placeholder tipografi** saat cover gagal dimuat → tidak ada kotak abu-abu kosong. |
| `SkeletonRows` | `Components.kt` | Loading awal memakai skeleton, bukan progress bar di tengah layar. |

## 3. Navigasi (Implemented)

* `NavigationBar` Material 3 dengan 5 tujuan sesuai prompt: **Home, Discover, Library, Activity, Settings** (`MainActivity.kt::LureadRoot`).
* Bottom bar **hanya** tampil di layar tingkat atas; di dalam Reader/Detail/Translator disembunyikan agar membaca/kerja tidak terganggu.
* Navigasi dalam aplikasi memakai `navigation-compose` dengan argumen tipe-amannya (`bookId: Int`, `text: String`).

## 4. Motion (Implemented, terbatas)

* `MotionFade` (fade 180 ms) untuk masuk/keluar layar dan overlay reader.
* Tidak ada animasi parallax/hero/kartu melayang: pada aplikasi baca, gerakan besar terasa mengganggu dan menambah beban persepsi.
* Toggle "kurangi gerak" global **belum** ada (Planned, lihat `feature-matrix.md`); saat ini animasi sudah sengaja dibuat pendek dan non-esensial.

## 5. Aksesibilitas (sebagian Implemented)

* Target sentuh tombol navigasi & aksi memakai komponen M3 bawaan (min 48dp).
* Ikon aksi selalu punya `contentDescription`; `BookCover` memakai judul sebagai deskripsi.
* Ukuran teks baca pengatur (14–30 sp) memakai unit sp, bukan dp.
* Yang **belum**: uji TalkBack nyata, label untuk seluruh kontrol reader, fokus keyboard.

## 6. Keputusan anti "AI slop" yang konkret

| Tanda AI-slop generik | Yang dilakukan di Luread |
| --- | --- |
| Gradient ungu-biru + judul besar kosong | Tidak ada gradient dekoratif; header memakai tipografi + aksen tunggal. |
| Semua kartu identik rounded 24dp + bayangan | Radius & elevasi konsisten tapi rendah; cover memimpin hierarki. |
| Teks placeholder "Lorem"/"Discover amazing books" | Semua teks memakai `strings.xml` dan menjelaskan data nyata (jumlah hasil dari API, status unduhan). |
| Warna status hijau/merah tanpa ikon | Status unduhan selalu memakai ikon + label teks. |
| Animasi lama (500 ms+) | Fade 180 ms; overlay reader langsung menyembunyikan diri setelah idle. |

## 7. Yang tidak diverifikasi

* Tampilan nyata di layar (portrait/landscape, tablet, dark mode) — **belum pernah dijalankan**.
* Kontras warna terhadap WCAG dihitung secara otomatis — baru dipilih secara manual.
* Perilaku font sistem / Dynamic Type ekstrem.
