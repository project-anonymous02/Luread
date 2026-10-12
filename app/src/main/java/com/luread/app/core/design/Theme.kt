package com.luread.app.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sistem desain Luread.
 *
 * Arah visual: kertas hangat + tinta gelap + satu aksen tanah liat, judul
 * memakai serif agar terasa seperti halaman cetak, bukan dasbor aplikasi.
 * Keputusan ruang, radius, dan gerak dikumpulkan di sini supaya layar mana pun
 * konsisten tanpa angka ajaib tersebar di file UI.
 */

// ---------------------------------------------------------------------------
// Palet
// ---------------------------------------------------------------------------

object LureadColors {
    // Netral hangat
    val Paper = Color(0xFFFAF8F5)
    val PaperRaised = Color(0xFFFFFFFF)
    val Ink = Color(0xFF1B1B19)
    val InkSoft = Color(0xFF51443B)
    val Line = Color(0xFFDED3C8)

    val NightPaper = Color(0xFF131514)
    val NightRaised = Color(0xFF1C1E1D)
    val NightInk = Color(0xFFF3EEE7)
    val NightInkSoft = Color(0xFFB6A79C)
    val NightLine = Color(0xFF3A3D3B)

    // Aksen
    val Clay = Color(0xFFA34A21)
    val ClayContainer = Color(0xFFFFDBCB)
    val OnClayContainer = Color(0xFF3A0B00)

    val Pine = Color(0xFF2F5D50)
    val PineContainer = Color(0xFFB2E2D2)

    val NightClay = Color(0xFFFFB59B)
    val NightClayContainer = Color(0xFF793516)
    val NightOnClayContainer = Color(0xFFFFDBCB)
    val NightPine = Color(0xFF99CCBA)

    // Status
    val Danger = Color(0xFF9E3B32)
    val Warning = Color(0xFF9A6B12)
    val Success = Color(0xFF2F6B45)
}

/** Preset aksen yang bisa dipilih pengguna di Settings. */
enum class AccentPreset(val label: String, val light: Color, val dark: Color) {
    Clay("Clay", Color(0xFFA34A21), Color(0xFFFFB59B)),
    Pine("Pine", Color(0xFF2F5D50), Color(0xFF99CCBA)),
    Indigo("Indigo", Color(0xFF33477A), Color(0xFFAFC3FF)),
    Plum("Plum", Color(0xFF6E3358), Color(0xFFEFB8DB)),
}

/** Mode gelap/terang. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Radius sudut yang dipakai komponen. */
enum class RadiusPreset(val dp: Dp) {
    Soft(4.dp), Rounded(10.dp), Pill(20.dp)
}

// ---------------------------------------------------------------------------
// Palet Material 3 yang diturunkan dari warna dasar
// ---------------------------------------------------------------------------

private fun lightScheme(accent: AccentPreset): ColorScheme = lightColorScheme(
    primary = accent.light,
    onPrimary = Color.White,
    primaryContainer = if (accent == AccentPreset.Clay) LureadColors.ClayContainer else accent.light.copy(alpha = 0.14f),
    onPrimaryContainer = if (accent == AccentPreset.Clay) LureadColors.OnClayContainer else accent.light,
    secondary = LureadColors.Pine,
    onSecondary = Color.White,
    secondaryContainer = LureadColors.PineContainer,
    onSecondaryContainer = Color(0xFF0C2C23),
    background = LureadColors.Paper,
    onBackground = LureadColors.Ink,
    surface = LureadColors.PaperRaised,
    onSurface = LureadColors.Ink,
    surfaceVariant = Color(0xFFF0E7DD),
    onSurfaceVariant = LureadColors.InkSoft,
    outline = LureadColors.Line,
    outlineVariant = LureadColors.Line,
    error = LureadColors.Danger,
    onError = Color.White,
)

private fun darkScheme(accent: AccentPreset): ColorScheme = darkColorScheme(
    primary = accent.dark,
    onPrimary = Color(0xFF2B1105),
    primaryContainer = if (accent == AccentPreset.Clay) LureadColors.NightClayContainer else accent.dark.copy(alpha = 0.20f),
    onPrimaryContainer = if (accent == AccentPreset.Clay) LureadColors.NightOnClayContainer else accent.dark,
    secondary = LureadColors.NightPine,
    onSecondary = Color(0xFF0A2A22),
    secondaryContainer = Color(0xFF1F4A3E),
    onSecondaryContainer = LureadColors.PineContainer,
    background = LureadColors.NightPaper,
    onBackground = LureadColors.NightInk,
    surface = LureadColors.NightRaised,
    onSurface = LureadColors.NightInk,
    surfaceVariant = Color(0xFF2A2C2B),
    onSurfaceVariant = LureadColors.NightInkSoft,
    outline = LureadColors.NightLine,
    outlineVariant = LureadColors.NightLine,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF410002),
)

// ---------------------------------------------------------------------------
// Tipografi editorial
// ---------------------------------------------------------------------------

object LureadType {
    val Display = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.3).sp,
    )
    val Title = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    )
    val Section = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    )
    val Body = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    )
    val BodyStrong = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    )
    val Metadata = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    )
    val Label = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    )
}

/** Mapping gaya Luread ke Typography Material 3. */
val LureadTypography = Typography(
    displayLarge = LureadType.Display.copy(fontSize = 40.sp, lineHeight = 46.sp),
    displayMedium = LureadType.Display,
    displaySmall = LureadType.Display.copy(fontSize = 26.sp, lineHeight = 32.sp),
    headlineMedium = LureadType.Title,
    headlineSmall = LureadType.Title.copy(fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = LureadType.Title.copy(fontSize = 19.sp, lineHeight = 25.sp),
    titleMedium = LureadType.Section,
    titleSmall = LureadType.Section.copy(fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = LureadType.Body,
    bodyMedium = LureadType.Body,
    bodySmall = LureadType.Metadata,
    labelLarge = LureadType.Label.copy(fontSize = 13.sp, lineHeight = 17.sp),
    labelMedium = LureadType.Label,
    labelSmall = LureadType.Label.copy(fontSize = 11.sp, lineHeight = 14.sp),
)

// ---------------------------------------------------------------------------
// Ruang, radius, gerak
// ---------------------------------------------------------------------------
object LureadSpace {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 24.dp
    val Xxl = 32.dp
    val SectionGap = 28.dp
}

@Immutable
data class LureadExtras(
    val radius: RadiusPreset = RadiusPreset.Rounded,
    val reduceMotion: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val coverAspectRatio: Float = 0.68f,
    val gridColumns: Int = 3,
    val compactMetadata: Boolean = false,
)

val LocalLureadExtras = staticCompositionLocalOf { LureadExtras() }

// ---------------------------------------------------------------------------
// Theme
// ---------------------------------------------------------------------------

@Composable
fun LureadTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    accent: AccentPreset = AccentPreset.Clay,
    extras: LureadExtras = LureadExtras(),
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (dark) darkScheme(accent) else lightScheme(accent)
    val shapes = Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
        small = androidx.compose.foundation.shape.RoundedCornerShape(extras.radius.dp * 0.5f),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(extras.radius.dp),
        large = androidx.compose.foundation.shape.RoundedCornerShape(extras.radius.dp * 1.5f),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(extras.radius.dp * 2f),
    )

    CompositionLocalProvider(LocalLureadExtras provides extras) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LureadTypography,
            shapes = shapes,
            content = content,
        )
    }
}
