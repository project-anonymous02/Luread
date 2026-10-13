package com.luread.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.luread.app.core.design.LureadTheme
import com.luread.app.data.settings.UserSettings
import com.luread.app.ui.ActivityScreen
import com.luread.app.ui.BookDetailScreen
import com.luread.app.ui.DiscoverScreen
import com.luread.app.ui.HomeScreen
import com.luread.app.ui.LibraryScreen
import com.luread.app.ui.LocalAppContainer
import com.luread.app.ui.ReaderScreen
import com.luread.app.ui.SettingsScreen
import com.luread.app.ui.TranslatorScreen
import com.luread.app.R

/** Rute navigasi utama. */
object Routes {
    const val HOME = "home"
    const val DISCOVER = "discover"
    const val LIBRARY = "library"
    const val ACTIVITY = "activity"
    const val SETTINGS = "settings"
    const val BOOK = "book/{bookId}"
    const val READ = "read/{bookId}"
    const val TRANSLATOR = "translator?text={text}"

    fun book(id: String): String = "book/$id"
    fun read(id: String): String = "read/$id"
    fun translator(text: String? = null): String =
        if (text.isNullOrEmpty()) "translator?text=" else "translator?text=${android.net.Uri.encode(text)}"

    val topLevel = setOf(HOME, DISCOVER, LIBRARY, ACTIVITY, SETTINGS)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as LureadApp).container

        setContent {
            val settings by container.settings.settings
                .collectAsStateWithLifecycle(initialValue = UserSettings())

            LureadTheme(mode = settings.themeMode, accent = settings.accent) {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    LureadRoot()
                }
            }
        }
    }
}

@Composable
private fun LureadRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val onTopLevel = currentRoute in Routes.topLevel

    Scaffold(
        bottomBar = {
            if (onTopLevel) {
                LureadBottomBar(navController, currentRoute)
            }
        },
    ) { padding ->
        AppNavHost(
            navController = navController,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun LureadBottomBar(navController: NavHostController, currentRoute: String?) {
    val items = listOf(
        Triple(Routes.HOME, Icons.Outlined.Home, R.string.nav_home),
        Triple(Routes.DISCOVER, Icons.Outlined.Search, R.string.nav_discover),
        Triple(Routes.LIBRARY, Icons.Outlined.MenuBook, R.string.nav_library),
        Triple(Routes.ACTIVITY, Icons.Outlined.History, R.string.nav_activity),
        Triple(Routes.SETTINGS, Icons.Outlined.Settings, R.string.nav_settings),
    )

    NavigationBar {
        items.forEach { (route, icon, labelRes) ->
            NavigationBarItem(
                selected = currentRoute == route,
                onClick = {
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(stringResource(labelRes)) },
            )
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenBook = { navController.navigate(Routes.book(it)) },
                onOpenDiscover = { navController.navigate(Routes.DISCOVER) },
            )
        }
        composable(Routes.DISCOVER) {
            DiscoverScreen(onOpenBook = { navController.navigate(Routes.book(it)) })
        }
        composable(Routes.LIBRARY) {
            LibraryScreen(onOpenBook = { navController.navigate(Routes.book(it)) })
        }
        composable(Routes.ACTIVITY) {
            ActivityScreen(onOpenBook = { navController.navigate(Routes.book(it)) })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onOpenTranslator = { navController.navigate(Routes.translator()) },
            )
        }
        composable(
            route = Routes.BOOK,
            arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
        ) { entry ->
            val bookId = entry.arguments?.getString("bookId") ?: return@composable
            BookDetailScreen(
                bookId = bookId,
                onBack = { navController.popBackStack() },
                onRead = { navController.navigate(Routes.read(it)) },
                onTranslate = { navController.navigate(Routes.translator(it)) },
            )
        }
        composable(
            route = Routes.READ,
            arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
        ) { entry ->
            val bookId = entry.arguments?.getString("bookId") ?: return@composable
            ReaderScreen(
                bookId = bookId,
                onBack = { navController.popBackStack() },
                onTranslate = { navController.navigate(Routes.translator(it)) },
            )
        }
        composable(
            route = Routes.TRANSLATOR,
            arguments = listOf(navArgument("text") {
                type = NavType.StringType
                defaultValue = ""
            }),
        ) { entry ->
            TranslatorScreen(
                initialText = entry.arguments?.getString("text"),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
