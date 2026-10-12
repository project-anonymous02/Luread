package com.luread.app.ui

import androidx.compose.runtime.compositionLocalOf
import com.luread.app.AppContainer

/** Akses container dependensi dari Composable tanpa bantuan framework DI. */
val LocalAppContainer = compositionLocalOf<AppContainer> {
    error("AppContainer belum disediakan")
}
