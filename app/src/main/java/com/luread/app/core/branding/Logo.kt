package com.luread.app.core.branding

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat

/**
 * Akses logo resmi tanpa referensi resource statis.
 *
 * File logo disediakan pemilik project di `image/logo.png` dan disalin oleh task
 * Gradle `:app:integrateLogo` ke `res/drawable-nodpi/logo.<png|jpg>` setelah magic
 * byte divalidasi (PNG maupun JPEG diterima). AndroidManifest menunjuk
 * `@drawable/logo` sebagai ikon launcher; objek ini dipakai untuk menampilkan
 * logo di dalam aplikasi (mis. halaman About) tanpa menghardcode ID resource,
 * sehingga kode tetap sah walau berkas logo diganti ukurannya.
 */
object Logo {
    const val RESOURCE_NAME: String = "logo"

    /** true bila logo resmi sudah terintegrasi ke dalam APK. */
    fun isAvailable(context: Context): Boolean = resourceId(context) != 0

    /** Drawable logo resmi, atau null bila belum tersedia. */
    fun drawable(context: Context): Drawable? {
        val id = resourceId(context)
        if (id == 0) return null
        return ContextCompat.getDrawable(context, id)
    }

    private fun resourceId(context: Context): Int =
        context.resources.getIdentifier(RESOURCE_NAME, "drawable", context.packageName)
}
