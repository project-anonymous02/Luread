#!/usr/bin/env python3
"""Verifikasi statis Luread — tanpa dependensi eksternal.

Dipakai di lingkungan pengembangan (tanpa JDK/Android SDK) dan di CI sebagai
langkah pertama. Pemeriksaan yang dilakukan:

  1. gradle/libs.versions.toml valid (tomllib) dan setiap alias yang dirujuk
     build.gradle.kts benar-benar ada.
  2. AndroidManifest.xml dan file res valid XML; tidak ada android:icon
     (logo resmi belum tersedia).
  3. Setiap R.string.X yang dipakai kode ada di strings.xml (dan sebaliknya).
  4. Struktur paket Kotlin cocok dengan lokasi file dan kurung seimbang.
  5. Tidak ada pola sintaks rusak yang pernah muncul selama pengembangan
     (mis. `.androidx.compose.`).
   6. Workflow GitHub Actions bebas tab, memakai tag aksi yang diverifikasi.
   7. Gradle wrapper resmi (gradlew, gradlew.bat, jar, properties) ada dan
      menunjuk versi yang sama dengan libs.versions.toml.
   8. Berkas wajib proyek (README, docs, workflow, manifest) ada dan tidak
      kosong.
   9. Logo resmi `image/logo.png` ada dan magic byte-nya PNG atau JPEG, dan
      manifest menunjuk `@drawable/logo`.

Keluar 0 bila lolos; 1 bila ada kegagalan, dengan rincian di stdout.
"""

from __future__ import annotations

import re
import sys
import tomllib
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []
warnings: list[str] = []


def fail(msg: str) -> None:
    errors.append(msg)


def warn(msg: str) -> None:
    warnings.append(msg)


def check_versions_toml() -> dict:
    path = ROOT / "gradle" / "libs.versions.toml"
    if not path.is_file():
        fail("gradle/libs.versions.toml tidak ada")
        return {}
    try:
        data = tomllib.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:  # noqa: BLE001
        fail(f"libs.versions.toml tidak valid: {exc}")
        return {}
    for section in ("versions", "libraries", "plugins"):
        if section not in data:
            fail(f"libs.versions.toml kekurangan bagian [{section}]")
    return data


def gradle_accessors(text: str) -> set[str]:
    """Kumpulkan rujukan `libs.xxx.yyy` dari skrip Gradle."""
    refs = set()
    for match in re.finditer(r"\blibs\.([A-Za-z0-9_.]+)", text):
        refs.add(match.group(1))
    return refs


def canonical_accessors(toml: dict) -> set[str]:
    """Aksesur kanonik per bagian: `libraries.androidx.core.ktx`, dst."""
    accessors: set[str] = set()
    for section in ("libraries", "plugins", "bundles", "versions"):
        for key in toml.get(section, {}):
            accessors.add(f"{section}.{key.replace('-', '.')}")
    return accessors


def check_gradle_references(toml: dict) -> None:
    known = canonical_accessors(toml)

    def canonical(ref: str) -> str:
        head = ref.split(".")[0]
        if head in {"plugins", "bundles", "versions"}:
            return ref
        return f"libraries.{ref}"

    for script in [ROOT / "build.gradle.kts", ROOT / "settings.gradle.kts", ROOT / "app" / "build.gradle.kts"]:
        if not script.is_file():
            fail(f"{script.relative_to(ROOT)} tidak ada")
            continue
        text = script.read_text(encoding="utf-8")
        for ref in sorted(gradle_accessors(text)):
            cleaned = ref.rstrip(".").removesuffix(".get")
            if canonical(cleaned) not in known:
                fail(f"{script.name}: accessor 'libs.{ref}' tidak ada di toml")


def check_manifest() -> None:
    path = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
    try:
        tree = ET.parse(path)
    except Exception as exc:  # noqa: BLE001
        fail(f"AndroidManifest.xml tidak valid: {exc}")
        return
    root = tree.getroot()
    ns = {"android": "http://schemas.android.com/apk/res/android"}
    application = root.find("application")
    if application is None:
        fail("manifest tanpa <application>")
        return
    ns_android = f"{{{ns['android']}}}"
    if application.get(f"{ns_android}icon") != "@drawable/logo":
        fail("application android:icon harus @drawable/logo (logo resmi, bukan ikon buatan)")
    name = application.get(f"{{{ns['android']}}}name")
    if name != ".LureadApp":
        fail(f"application android:name = {name}, diharapkan .LureadApp")
    activity = application.find("activity")
    if activity is None or activity.get(f"{{{ns['android']}}}name") != ".MainActivity":
        fail("activity launcher .MainActivity tidak ditemukan")
    perms = {p.get(f"{{{ns['android']}}}name") for p in root.findall("uses-permission")}
    for required in ("android.permission.INTERNET", "android.permission.WAKE_LOCK"):
        if required not in perms:
            fail(f"izin {required} hilang")
    queries = root.find("queries")
    if queries is None:
        fail("<queries> untuk ACTION_PROCESS_TEXT hilang")


def check_resources() -> None:
    strings_path = ROOT / "app" / "src" / "main" / "res" / "values" / "strings.xml"
    try:
        tree = ET.parse(strings_path)
    except Exception as exc:  # noqa: BLE001
        fail(f"strings.xml tidak valid: {exc}")
        return

    declared = set()
    for node in tree.getroot().findall("string"):
        name = node.get("name")
        if name:
            declared.add(name)

    used: set[str] = set()
    for kt in (ROOT / "app").rglob("*.kt"):
        for match in re.finditer(r"R\.string\.([a-z0-9_]+)", kt.read_text(encoding="utf-8")):
            used.add(match.group(1))

    for missing in sorted(used - declared):
        fail(f"R.string.{missing} dipakai kode tetapi tidak ada di strings.xml")
    for unused in sorted(declared - used):
        warn(f"string '{unused}' dideklarasikan tetapi tidak dipakai")

    for res in (ROOT / "app" / "src" / "main" / "res").rglob("*.xml"):
        if res.name == "strings.xml":
            continue
        try:
            ET.parse(res)
        except Exception as exc:  # noqa: BLE001
            fail(f"{res.relative_to(ROOT)} bukan XML valid: {exc}")


def strip_kotlin_noise(text: str) -> str:
    """Buang komentar dan isi string lewat satu pass scanner.

    Urutan penting: komentar yang ada di dalam string (mis. URL `https://…`)
    tidak boleh memotong string tersebut, sehingga string dibuang lebih dulu
    dan komentar hanya dikenali di luar string.
    """
    out: list[str] = []
    i = 0
    n = len(text)
    while i < n:
        ch = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if ch == "/" and nxt == "/":
            while i < n and text[i] != "\n":
                i += 1
            continue
        if ch == "/" and nxt == "*":
            end = text.find("*/", i + 2)
            i = n if end < 0 else end + 2
            continue
        if ch == '"':
            # String tiga kutip dulu, baru satu kutip.
            if text.startswith('"""', i):
                end = text.find('"""', i + 3)
                out.append('""')
                i = n if end < 0 else end + 3
                continue
            i += 1
            while i < n:
                if text[i] == "\\":
                    i += 2
                    continue
                if text[i] == '"':
                    i += 1
                    break
                i += 1
            out.append('""')
            continue
        if ch == "'":
            i += 1
            while i < n:
                if text[i] == "\\":
                    i += 2
                    continue
                if text[i] == "'":
                    i += 1
                    break
                i += 1
            out.append("''")
            continue
        out.append(ch)
        i += 1
    return "".join(out)


def check_kotlin_sources() -> None:
    for path in sorted((ROOT / "app").rglob("*.kt")):
        if "build" in path.relative_to(ROOT).parts:
            continue
        raw = path.read_text(encoding="utf-8")
        rel = path.relative_to(ROOT)
        if ".androidx." in raw:
            fail(f"{rel}: pola sintaks rusak '.androidx.'")

        match = re.search(r"^package\s+([\w.]+)", raw, flags=re.M)
        if not match:
            fail(f"{rel}: deklarasi package tidak ditemukan")
            continue
        package = match.group(1)
        if "/src/test/" in str(path):
            src_root = ROOT / "app" / "src" / "test" / "java"
        else:
            src_root = ROOT / "app" / "src" / "main" / "java"
        expected = ".".join(path.parent.relative_to(src_root).parts)
        if package != expected:
            fail(f"{rel}: package '{package}' tidak cocok dengan direktori '{expected}'")

        code = strip_kotlin_noise(raw)
        for opening, closing in (("{", "}"), ("(", ")"), ("[", "]")):
            if code.count(opening) != code.count(closing):
                fail(
                    f"{rel}: kurung {opening}{closing} tidak seimbang "
                    f"({code.count(opening)} vs {code.count(closing)})",
                )


REQUIRED_FILES = [
    "README.md",
    "docs/research.md",
    "docs/gutendex-api-audit.md",
    "docs/ui-research.md",
    "docs/architecture.md",
    "docs/feature-matrix.md",
    "docs/testing.md",
    "docs/translation-integration.md",
    ".github/workflows/build.yml",
    "gradle/libs.versions.toml",
    "app/build.gradle.kts",
    "app/src/main/AndroidManifest.xml",
]


PNG_MAGIC = b"\x89PNG\r\n\x1a\n"


def check_logo() -> None:
    """Logo resmi wajib ada dan berformat PNG atau JPEG (magic byte)."""
    path = ROOT / "image" / "logo.png"
    if not path.is_file():
        fail("image/logo.png tidak ada — logo resmi disediakan pemilik project di lokasi ini")
        return
    head = path.read_bytes()[:8]
    is_png = head[:8] == PNG_MAGIC
    is_jpeg = head[:3] == b"\xff\xd8\xff"
    if not (is_png or is_jpeg):
        fail("image/logo.png bukan PNG atau JPEG yang valid (magic byte tidak dikenali)")


def check_required_files() -> None:
    for rel in REQUIRED_FILES:
        path = ROOT / rel
        if not path.is_file():
            fail(f"{rel} tidak ada")
        elif path.stat().st_size == 0:
            fail(f"{rel} kosong")


def check_gradle_wrapper(toml: dict) -> None:
    """Wrapper harus file resmi Gradle, bukan jar hasil pembuatan sendiri."""
    wrapper_dir = ROOT / "gradle" / "wrapper"
    for rel in ("gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties"):
        if not (ROOT / rel).is_file():
            fail(f"{rel} tidak ada")
    gradlew = ROOT / "gradlew"
    if gradlew.is_file():
        head = gradlew.read_text(encoding="utf-8", errors="replace")[:80]
        if not head.startswith("#!/bin/sh"):
            fail("gradlew bukan skrip shell resmi (tidak diawali #!/bin/sh)")
        if not (gradlew.stat().st_mode & 0o111):
            fail("gradlew tidak punya bit eksekusi")
        if "gradle-wrapper.jar" not in gradlew.read_text(encoding="utf-8", errors="replace"):
            fail("gradlew tidak merujuk gradle-wrapper.jar")

    jar = wrapper_dir / "gradle-wrapper.jar"
    if jar.is_file():
        import zipfile

        try:
            with zipfile.ZipFile(jar) as zf:
                names = zf.namelist()
        except Exception as exc:  # noqa: BLE001
            fail(f"gradle-wrapper.jar bukan zip valid: {exc}")
            names = []
        if "org/gradle/wrapper/GradleWrapperMain.class" not in names:
            fail("gradle-wrapper.jar tidak memuat GradleWrapperMain — bukan wrapper resmi")

    props = wrapper_dir / "gradle-wrapper.properties"
    if props.is_file() and toml:
        version = toml.get("versions", {}).get("gradleWrapper")
        text = props.read_text(encoding="utf-8")
        if version and f"gradle-{version}-bin.zip" not in text:
            fail(f"gradle-wrapper.properties tidak menunjuk Gradle {version}")


def check_workflow() -> None:
    path = ROOT / ".github" / "workflows" / "build.yml"
    if not path.is_file():
        fail(".github/workflows/build.yml tidak ada")
        return
    text = path.read_text(encoding="utf-8")
    if "\t" in text:
        fail("workflow YAML mengandung tab")
    for tag in re.findall(r"uses:\s*(\S+)", text):
        # Berbentuk owner/repo@ref, boleh dengan sub-direktori (gradle/actions/setup-gradle@v6).
        if not re.match(r"^[a-zA-Z0-9._-]+/[a-zA-Z0-9._-]+(/[a-zA-Z0-9._-]+)*@[a-zA-Z0-9._-]+$", tag):
            fail(f"referensi aksi tidak berbentuk owner/repo@ref: {tag}")
    if "assembleDebug" not in text:
        fail("workflow tidak membangun assembleDebug")
    if "testDebugUnitTest" not in text:
        fail("workflow tidak menjalankan unit test")
    if "./gradlew" not in text:
        fail("workflow tidak memakai ./gradlew")


def main() -> int:
    toml = check_versions_toml()
    if toml:
        check_gradle_references(toml)
    check_manifest()
    check_resources()
    check_kotlin_sources()
    check_workflow()
    check_required_files()
    check_logo()
    check_gradle_wrapper(toml)

    for w in warnings:
        print(f"WARN  {w}")
    for e in errors:
        print(f"ERROR {e}")
    if errors:
        print(f"\nGagal: {len(errors)} masalah, {len(warnings)} peringatan.")
        return 1
    print(f"Lolos: 0 masalah, {len(warnings)} peringatan.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
