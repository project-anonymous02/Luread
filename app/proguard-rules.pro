# ProGuard/R8 rules untuk Luread.
#
# Pada rilis 0.1.0, minify dinonaktifkan (lihat app/build.gradle.kts) karena
# konfigurasi obfuskasi belum dapat diverifikasi di lingkungan pengembangan.
# Aturan di bawah disiapkan agar obfuskasi dapat diaktifkan kembali tanpa
# harus menebak-nebak kebutuhan library.

# --- kotlinx.serialization -------------------------------------------------
-keepattributes InnerClasses,EnclosingMethod,Signature
-keepattributes *Annotation*

-keep,includedescriptorclasses class com.luread.app.**$$serializer { *; }
-keepclassmembers class com.luread.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.luread.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- OkHttp / Ktor ---------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn kotlinx.coroutines.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# --- Readium ---------------------------------------------------------------
-keep class org.readium.r2.** { *; }
-keep class org.readium.navigator.** { *; }
-dontwarn org.readium.**

# --- Room ------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
