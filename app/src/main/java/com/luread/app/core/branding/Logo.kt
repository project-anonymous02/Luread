package com.luread.app.core.branding

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat

/**
 * Akses logo resmi tanpa referensi resource statis.
 *
 * File logo disediakan pemilik project di `image/logo.png` dan disalin oleh task
 * Gradle `:app:integrateLogo` ke `res/drawable-nodpi/logo.png` hanya bila file itu
 * benar-benar ada dan berformat PNG valid. Karena file tersebut belum tentu ada,
 * resource dirujuk lewat [Context.resources.getIdentifier] sehingga build tetap
 * sah ketika logo belum tersedia (tidak ada placeholder buatan).
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
