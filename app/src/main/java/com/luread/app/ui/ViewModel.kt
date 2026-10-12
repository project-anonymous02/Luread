package com.luread.app.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/** Membuat factory ViewModel sederhana tanpa framework DI. */
inline fun <reified VM : ViewModel> appFactory(noinline create: () -> VM): ViewModelProvider.Factory =
    viewModelFactory {
        initializer<VM> { create() }
    }

/** Ambil ViewModel yang sudah terhubung ke [androidx.compose.runtime.CompositionLocalProvider] container. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (com.luread.app.AppContainer) -> VM): VM {
    val container = LocalAppContainer.current
    return viewModel(factory = appFactory { create(container) })
}
