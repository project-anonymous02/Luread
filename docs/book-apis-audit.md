# Book APIs Audit — Open Library, Internet Archive, Wikisource Indonesia

Tanggal uji: **13 Oktober 2026**. Metode: `curl` langsung dari lingkungan
pengembangan (User-Agent `Luread/0.1.0`), tanpa API key — sesuai batasan
prompt. Semua endpoint di bawah divalidasi sebelum kode ditulis; keputusan
implementasi mengikuti hasil uji, bukan asumsi.

Status: setiap baris "Hasil" adalah pengamatan nyata. Parameter yang tidak
lulus uji **tidak ditawarkan di UI**.

---

## 1. Open Library (`https://openlibrary.org`)

### 1.1 Pencarian

```
GET /search.json?q=cerita+rakyat&language=ind&limit=2&fields=key,title,author_name,cover_i,ia,language,subject,first_publish_year,has_fulltext
```

* Hasil: **200**, `numFound: 520` (tanpa `language=ind`: 621) → filter
  `language=ind` **bekerja** (ISO 639-2).
* Contoh doc: `{"key":"/works/OL15948305W","title":"Kumpulan cerita rakyat
  Kabupaten Pangkep","author_name":["Syamsu Alam Nyonri"],"language":["ind"]}`.
* Field `cover_i` dan `ia` **opsional** (tidak selalu ada di doc) → DTO
  memberi default `null`/`[]`.

```
GET /search.json?limit=2&fields=…          # tanpa q
```

* Hasil: `numFound: 0` → **`q` wajib**. Kebijakan pencarian kosong:
  `q=subject:fiction` + `sort=editions` (diverifikasi: `numFound: 1688069`,
  hasil pertama *Pride and Prejudice*, *Alice's Adventures in Wonderland* —
  browse populer yang wajar).

* `language=eng` diverifikasi (1.755.354 hasil untuk `q=history`).

### 1.2 Detail karya

```
GET /works/OL66554W.json            → 200 (title, description string/objek, covers[], authors[])
GET /works/OL66554W/editions.json?limit=5
```

* Hasil edisi: `/books/OL49836838M ocaid=prideprejudice190000jane` —
  **`ocaid` menunjuk item Internet Archive**, dipakai untuk memperoleh file
  unduhan (lihat §2.2). Edisi tanpa `ocaid` dilewati.
* `description` kadang string, kadang `{"type":"text","value":…}` → keduanya
  ditangani.
* Nama penulis: `/authors/{id}.json` (2 penulis pertama diambil agar tidak
  membanjiri server).

### 1.3 Cover

```
GET https://covers.openlibrary.org/b/id/8231856-M.jpg → 200 image/jpeg
```

### 1.4 Yang tidak dipakai

* `lang=` (parameter UI) **bukan** filter hasil → tidak dikirim.
* Google Books (`www.googleapis.com/books/v1/volumes`) → **429
  `quota_limit_value: 0`** pada keyless request → sumber **dihapus dari
  rencana** (prompt melarang API key).

---

## 2. Internet Archive (`https://archive.org`)

### 2.1 Pencarian

```
GET /advancedsearch.php?q=mediatype:texts AND language:ind&rows=2&output=json&fl[]=identifier&fl[]=title
```

* Hasil: **200**, `numFound: 20456` untuk item berlabel `ind` (kualitas label
  IA campuran — ada item non-buku ikut tercatat; tetap filter terbaik yang
  tersedia).
* `q=mediatype:texts AND title:(cerita) AND format:(EPUB OR "Text PDF" OR DjVuTXT)`
  → 91 hasil, contoh: *Boy Candra Senja Hujan…*, *Inventarisasi Cerita Rakyat
  di Jombang* — buku-buku Indonesia nyata.

Browse tanpa kata kunci (keputusan):

* `mediatype:texts AND format:EPUB` + `sort[]=downloads desc` murni → hasil
  teratas sampah (*Surah Al Mulk*, rip *PDFDrive*) **di semua varian**
  (`-collection:…`, `collection:opensource`) → tetap tidak bisa diandalkan.
* Kebijakan akhir: pencarian kosong → `mediatype:texts AND format:(EPUB OR
  "Text PDF" OR DjVuTXT) AND subject:fiction AND sort[]=downloads desc`
  (uji: *Diary of a Wimpy Kid*, *Harrison Bergeron* — jauh lebih masuk akal).

Bahasa (uji per kode, `numFound`):

| Kode | Hasil | Kebijakan |
| --- | --- | --- |
| `language:ind` | 20.456 | filter `id` → `ind` |
| `language:eng` | cocok (uji `title:(wizard)` 1665) | filter `en` → `eng` |
| `language:fre` / `ger` / `spa` | 779.847 / 897.433 / 1.052.927 | ISO 639-2 dipakai |
| `language:id` | hanya 109 | **tidak dipakai** (kodifikasi minoritas; `ind` benar) |

Field `fl[]` diverifikasi: `identifier,title,creator,description,language,
downloads,subject,year` — `title`/`creator`/`language`/`description` **string
ATAU array** → dinormalisasi di mapper.

### 2.2 Metadata & unduhan

```
GET /metadata/prideprejudice01aust
→ server ia600500.us.archive.org, files termasuk:
  prideprejudice01aust.epub        (EPUB, 1436755)
  prideprejudice01aust.pdf         (Text PDF, 10702341)
  prideprejudice01aust_djvu.txt    (DjVuTXT, 845080)
```

```
GET /download/prideprejudice01aust/prideprejudice01aust_djvu.txt -L
  → 200, 845080 byte (sama persis dengan size di metadata; isi teks buku nyata)
GET /download/prideprejudice01aust/prideprejudice01aust.epub
  → 302 redirect ke node perpusakaan (HttpURLConnection mengikuti otomatis)
GET https://archive.org/services/img/prideprejudice01aust → 200 image/jpeg (cover)
```

Aturan pemilihan file (`ArchiveFormats`, diuji unit test):

* EPUB: nama `*.epub` / format `EPUB`;
* PDF: nama `*.pdf` **bukan** `*_text.pdf` lebih dulu, baru turunan teks;
* teks: `*_djvu.txt` / format `DjVuTXT`;
* URL per segmen di-encode (nama file boleh mengandung spasi).

---

## 3. Wikisource Indonesia (`https://id.wikisource.org`)

### 3.1 Pencarian & browse

```
GET /w/api.php?action=query&list=search&srsearch=cerita+rakyat&srlimit=20&format=json&formatversion=2
```

* Hasil: **200**, `totalhits: 33`, 20 baris `pageid`+`title`;
  lanjutan via `continue.sroffset` (uji `sroffset=2` → 2 hasil berbeda ✓).

```
GET /w/api.php?action=query&list=allpages&aplimit=3&format=json&formatversion=2
```

* Hasil: **200** — browse default tanpa kata kunci, lanjutan via
  `continue.apcontinue` (judul halaman terakhir, di-encode sebagai parameter).

### 3.2 Konten halaman — `prop=extracts` TIDAK berfungsi

Uji (pageid 33610, 5 varian: `explaintext=1`, tanpa `exlimit`, `exintro`,
`exchars`, judul via `titles=`):

* Semua membalas `extract` **kosong** (`len=0`), bahkan mode HTML
  menghasilkan `<p><span></span></p>` — generator ekstraksi wiki ini rusak/
  tidak kompatibel → **keputusan: tidak memakai `prop=extracts`**.

Pengganti yang diverifikasi:

```
GET /w/api.php?action=parse&pageid=33610&prop=text&format=json&formatversion=2
→ parse.text: 52.519 karakter HTML isi artikel ✓
```

* Rangkuman layar detail: `parse.text` → `HtmlStrip.toText(...).take(400)`
  (fungsi murni, diuji unit test).
* Unduhan baca: `parse.text` dibungkus dokumen HTML minimum
  (`WikisourceApi.wrapDocument`) lalu disimpan lokal → dibaca `HtmlReader`
  (WebView, JS mati). Tautan gambar Wikimedia dimuat jaringan saat membaca;
  teks tetap utuh offline.

### 3.3 Batasan jujur

* Semua konten = bahasa Indonesia (wiki khusus `id`) → filter bahasa/topik/
  format disembunyikan untuk sumber ini.
* Tanpa cover (API halaman tidak menyediakan gambar sampul andal) → kartu
  memakai placeholder.

---

## 4. Sumber yang DIELIMINASI (dengan bukti)

| Sumber | Alasan |
| --- | --- |
| Google Books | Keyless `429 quota_limit_value: 0` → butuh API key (dilarang prompt) |
| `repositori.kemdikbud.go.id` | DNS tidak teratasi (NXDOMAIN) |
| `repository.uinjkt.ac.id` | Error Tomcat 404/500 pada path API lama |
| Gutenberg gavici/other mirror | Tidak diuji — cukup satu sumber Gutenberg (Gutendex sudah teraudit penuh) |

---

## 5. Pemetaan kunci & filter per sumber

Kunci komposit (Room PK String, schema v2):

| Sumber | Kunci | Sumber unduhan |
| --- | --- | --- |
| Gutenberg | `gutenberg:1342` | URL format dari Gutendex |
| Open Library | `openlibrary:OL66554W` | `editions → ocaid → archive.org/metadata` |
| Internet Archive | `archive:{identifier}` | `archive.org/download/{id}/{file}` |
| Wikisource id | `wikisource:{pageid}` | HTML `action=parse` disimpan lokal |

Filter UI per sumber:

| Filter | Gutenberg | Open Library | Archive | Wikisource |
| --- | --- | --- | --- | --- |
| Search | ✓ | ✓ (`subject:fiction` bila kosong) | ✓ (`title:(…)`; `subject:fiction` bila kosong) | ✓ (`list=search`; `allpages` bila kosong) |
| Language | ✓ (10 bahasa) | ✓ (ISO 639-2) | ✓ (ISO 639-2) | — (disembunyikan) |
| Topic | ✓ | — | — | — |
| Format | ✓ | — | — | — |
