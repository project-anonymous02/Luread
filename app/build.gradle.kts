plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.luread.app"
    // compileSdk 37: diminta AAR navigation-compose 2.10.2 dan Readium 3.4.0
    // (checkDebugAarMetadata gagal pada 36 — dibuktikan CI 2026-10-12).
    compileSdk = 37

    defaultConfig {
        applicationId = "com.luread.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // R8 sengaja dimatikan pada rilis 0.1.0. Konfigurasi obfuskasi untuk
            // kotlinx.serialization, Readium, dan Ktor belum dapat diverifikasi
            // pada lingkungan pengembangan saat ini (lihat docs/testing.md).
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        // Readium 3.4 mensyaratkan core library desugaring (AAR metadata).
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coil.compose)

    implementation(libs.readium.shared)
    implementation(libs.readium.streamer)
    implementation(libs.readium.navigator)
    implementation(libs.readium.navigator.web.reflowable)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

// ---------------------------------------------------------------------------
// Integrasi logo resmi.
//
// Logo disediakan pemilik project di image/logo.png. Ekstensi mengikuti nama
// file yang diminta, tetapi isi file divalidasi lewat magic byte: PNG maupun
// JPEG diterima (dan disalin dengan ekstensi yang benar, nama resource tetap
// "logo"). Task ini:
//   * tidak pernah membuat atau mengganti file logo,
//   * menggagalkan build dengan pesan jelas bila file hilang atau formatnya
//     tidak dikenali, karena AndroidManifest menunjuk @drawable/logo.
// ---------------------------------------------------------------------------
val integrateLogo = tasks.register("integrateLogo") {
    group = "luread"
    description = "Validasi magic byte dan salin image/logo.png ke res/drawable-nodpi."
    doLast {
        val src = rootProject.file("image/logo.png")
        if (!src.isFile) {
            throw GradleException(
                "image/logo.png tidak ditemukan. Sediakan logo resmi (PNG atau JPEG) " +
                    "di image/logo.png; jangan menggantinya dengan berkas buatan sendiri.",
            )
        }
        val head = src.inputStream().use { input ->
            val bytes = ByteArray(8)
            var read = 0
            while (read < bytes.size) {
                val n = input.read(bytes, read, bytes.size - read)
                if (n < 0) break
                read += n
            }
            bytes.copyOf(read)
        }
        val pngMagic = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val isPng = head.size >= 8 && head.copyOf(8).contentEquals(pngMagic)
        val isJpeg = head.size >= 3 &&
            head[0] == 0xFF.toByte() &&
            head[1] == 0xD8.toByte() &&
            head[2] == 0xFF.toByte()
        val ext = when {
            isPng -> "png"
            isJpeg -> "jpg"
            else -> throw GradleException(
                "image/logo.png bukan PNG atau JPEG yang valid (magic byte tidak dikenali). " +
                    "Perbaiki berkasnya; build dihentikan agar ikon tidak pernah palsu.",
            )
        }
        val outDir = file("src/main/res/drawable-nodpi")
        outDir.mkdirs()
        // Buang ekstensi lain agar tidak ada dua resource bernama "logo".
        listOf("png", "jpg", "jpeg")
            .filter { it != ext }
            .forEach { other -> file("src/main/res/drawable-nodpi/logo.$other").delete() }
        src.copyTo(file("src/main/res/drawable-nodpi/logo.$ext"), overwrite = true)
        logger.lifecycle("Luread: logo resmi disalin ke res/drawable-nodpi/logo.$ext")
    }
}

tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(integrateLogo) }
