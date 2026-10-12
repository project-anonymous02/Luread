plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.luread.app"
    compileSdk = 36

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
}

// ---------------------------------------------------------------------------
// Integrasi logo resmi (disediakan pemilik project di image/logo.png).
//
// Aturan yang diikuti:
//   * Tidak pernah membuat atau mengganti file logo.
//   * Tidak pernah menggagalkan build bila file belum tersedia.
//   * Hanya menyalin file yang benar-benar PNG valid (cek magic byte).
//   * Aplikasi merujuk logo saat ini lewat getIdentifier() sehingga build tetap
//     sah baik logo ada maupun tidak (lihat core/branding/Logo.kt).
// ---------------------------------------------------------------------------
val integrateLogo = tasks.register("integrateLogo") {
    group = "luread"
    description = "Salin image/logo.png ke res bila tersedia dan valid PNG; dilewati bila tidak ada."
    doLast {
        val src = rootProject.file("image/logo.png")
        if (!src.isFile) {
            logger.lifecycle("Luread: image/logo.png belum tersedia -> integrasi logo dilewati.")
            return@doLast
        }
        val magic = src.inputStream().use { input ->
            val bytes = ByteArray(8)
            var read = 0
            while (read < 8) {
                val n = input.read(bytes, read, 8 - read)
                if (n < 0) break
                read += n
            }
            if (read < 8) ByteArray(0) else bytes
        }
        val pngMagic = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        if (!magic.contentEquals(pngMagic)) {
            logger.warn("Luread: image/logo.png bukan PNG valid -> tidak disalin, build dilanjutkan.")
            return@doLast
        }
        val outDir = file("src/main/res/drawable-nodpi")
        outDir.mkdirs()
        src.copyTo(file("src/main/res/drawable-nodpi/logo.png"), overwrite = true)
        logger.lifecycle("Luread: logo resmi disalin ke src/main/res/drawable-nodpi/logo.png")
    }
}

tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(integrateLogo) }
