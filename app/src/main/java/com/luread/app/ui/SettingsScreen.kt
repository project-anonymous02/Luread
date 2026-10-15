package com.luread.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luread.app.R
import com.luread.app.core.branding.Logo
import com.luread.app.core.design.AccentPreset
import com.luread.app.core.design.LureadSpace.Lg
import com.luread.app.core.design.LureadSpace.Md
import com.luread.app.core.design.LureadSpace.SectionGap
import com.luread.app.core.design.LureadSpace.Sm
import com.luread.app.core.design.RadiusPreset
import com.luread.app.core.design.SectionHeader
import com.luread.app.core.design.ThemeMode

@Composable
fun SettingsScreen(onOpenTranslator: () -> Unit) {
    val vm = appViewModel { SettingsViewModel(it.settings, it.library) }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Lg),
        verticalArrangement = Arrangement.spacedBy(Sm),
    ) {
        Text(
            text = stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = Lg),
        )

        // ------------------------------------------------------------------
        // Tampilan
        // ------------------------------------------------------------------
        SectionHeader(title = stringResource(R.string.settings_appearance))
        ChipRow(
            options = ThemeMode.entries.map { mode ->
                mode.name to stringResource(
                    when (mode) {
                        ThemeMode.SYSTEM -> R.string.settings_theme_system
                        ThemeMode.LIGHT -> R.string.settings_theme_light
                        ThemeMode.DARK -> R.string.settings_theme_dark
                    },
                )
            },
            selected = state.themeMode.name,
            onSelect = { name ->
                ThemeMode.entries.firstOrNull { it.name == name }?.let(vm::setThemeMode)
            },
        )
        Text(
            text = stringResource(R.string.settings_accent),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChipRow(
            options = AccentPreset.entries.map { it.name to it.label },
            selected = state.accent.name,
            onSelect = { name ->
                AccentPreset.entries.firstOrNull { it.name == name }?.let(vm::setAccent)
            },
        )
        Text(
            text = stringResource(R.string.settings_custom_accent),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CustomAccentRow(
            selectedArgb = state.customAccentArgb,
            onSelect = vm::setCustomAccent,
        )
        Text(
            text = stringResource(R.string.settings_radius),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChipRow(
            options = RadiusPreset.entries.map { it.name to it.name },
            selected = state.cardRadius.name,
            onSelect = { name ->
                RadiusPreset.entries.firstOrNull { it.name == name }?.let(vm::setCardRadius)
            },
        )
        LabeledSlider(
            label = stringResource(R.string.settings_grid_columns, state.clampedGridColumns),
            value = state.gridColumns.toFloat(),
            onValueChange = { vm.setGridColumns(it.toInt()) },
            valueRange = 1f..6f,
            steps = 4,
        )
        LabeledSlider(
            label = stringResource(R.string.settings_cover_ratio, "%.2f".format(state.clampedCoverRatio)),
            value = state.coverAspectRatio,
            onValueChange = vm::setCoverAspectRatio,
            valueRange = 0.4f..1.5f,
            steps = 21,
        )
        ToggleRow(
            title = stringResource(R.string.settings_compact_metadata),
            checked = state.compactMetadata,
            onCheckedChange = vm::setCompactMetadata,
        )
        ToggleRow(
            title = stringResource(R.string.settings_reduce_motion),
            checked = state.reduceMotion,
            onCheckedChange = vm::setReduceMotion,
        )
        // Home section toggles
        Text(
            text = stringResource(R.string.settings_home_sections),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ToggleRow(
            title = stringResource(R.string.settings_show_continue_reading),
            checked = state.showContinueReading,
            onCheckedChange = vm::setShowContinueReading,
        )
        ToggleRow(
            title = stringResource(R.string.settings_show_manga_home),
            checked = state.showMangaHome,
            onCheckedChange = vm::setShowMangaHome,
        )
        ToggleRow(
            title = stringResource(R.string.settings_show_comics_home),
            checked = state.showComicsHome,
            onCheckedChange = vm::setShowComicsHome,
        )
        ToggleRow(
            title = stringResource(R.string.settings_show_knowledge_home),
            checked = state.showKnowledgeHome,
            onCheckedChange = vm::setShowKnowledgeHome,
        )
        ToggleRow(
            title = stringResource(R.string.settings_show_stats_home),
            checked = state.showStatsHome,
            onCheckedChange = vm::setShowStatsHome,
        )

        // ------------------------------------------------------------------
        // Membaca
        // ------------------------------------------------------------------
        SectionHeader(title = stringResource(R.string.settings_reading))
        LabeledSlider(
            label = stringResource(R.string.settings_font_size, state.clampedFontSize.toInt()),
            value = state.fontSizeSp,
            onValueChange = vm::setFontSize,
            valueRange = 10f..40f,
            steps = 29,
        )
        LabeledSlider(
            label = stringResource(R.string.settings_line_height, "%.1f×".format(state.clampedLineHeight)),
            value = state.lineHeightMul,
            onValueChange = vm::setLineHeight,
            valueRange = 1.0f..2.5f,
            steps = 14,
        )
        LabeledSlider(
            label = stringResource(R.string.settings_dim_overlay, (state.clampedDim * 100).toInt()),
            value = state.dimOverlay,
            onValueChange = vm::setDimOverlay,
            valueRange = 0f..0.8f,
            steps = 15,
        )
        ToggleRow(
            title = stringResource(R.string.settings_page_animation),
            checked = state.pageAnimation,
            onCheckedChange = vm::setPageAnimation,
        )
        if (state.pageAnimation) {
            Text(
                text = stringResource(R.string.settings_page_animation_style, state.pageAnimationStyle),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChipRow(
                options = listOf(
                    "slide" to "Slide",
                    "fade" to "Fade",
                    "none" to "None",
                ),
                selected = state.pageAnimationStyle,
                onSelect = vm::setPageAnimationStyle,
            )
        }
        // LUREAD-FEAT: arah baca umum — semua halaman digeser kiri/kanan;
        // tidak ada lagi mode scroll/webtoon (dilarang vertikal).
        Text(
            text = stringResource(R.string.settings_reading_direction, state.readingDirection),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChipRow(
            options = listOf(
                "ltr" to "Kiri → Kanan",
                "rtl" to "Kanan → Kiri",
            ),
            selected = state.readingDirection,
            onSelect = vm::setReadingDirection,
        )
        ToggleRow(
            title = stringResource(R.string.settings_keep_screen_on),
            checked = state.keepScreenOn,
            onCheckedChange = vm::setKeepScreenOn,
        )
        ToggleRow(
            title = stringResource(R.string.settings_track_stats),
            checked = state.trackReadingStats,
            onCheckedChange = vm::setTrackReadingStats,
        )
        Text(
            text = stringResource(R.string.settings_font_family, state.fontFamily),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChipRow(
            options = listOf(
                "default" to "Default",
                "serif" to "Serif",
                "sans" to "Sans-serif",
                "monospace" to "Monospace",
            ),
            selected = state.fontFamily,
            onSelect = vm::setFontFamily,
        )
        ToggleRow(
            title = stringResource(R.string.settings_justify_text),
            checked = state.justifyText,
            onCheckedChange = vm::setJustifyText,
        )
        LabeledSlider(
            label = stringResource(R.string.settings_brightness, (state.clampedBrightness * 100).toInt()),
            value = state.brightnessOverride,
            onValueChange = vm::setBrightnessOverride,
            valueRange = -1f..1f,
            steps = 19,
        )
        ToggleRow(
            title = stringResource(R.string.settings_volume_nav),
            checked = state.volumeKeyNavigation,
            onCheckedChange = vm::setVolumeKeyNavigation,
        )
        ToggleRow(
            title = stringResource(R.string.settings_tap_left_next),
            checked = state.tapLeftForNext,
            onCheckedChange = vm::setTapLeftForNext,
        )
        ToggleRow(
            title = stringResource(R.string.settings_show_page_numbers),
            checked = state.showPageNumbers,
            onCheckedChange = vm::setShowPageNumbers,
        )
        Text(
            text = stringResource(R.string.settings_manga_direction, state.mangaReadingDirection),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChipRow(
            options = listOf(
                "rtl" to "Kanan → Kiri (manga)",
                "ltr" to "Kiri → Kanan",
            ),
            selected = state.mangaReadingDirection,
            onSelect = vm::setMangaReadingDirection,
        )
        Text(
            text = stringResource(R.string.settings_komik_direction, state.komikReadingDirection),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChipRow(
            options = listOf(
                "ltr" to "Kiri → Kanan",
                "rtl" to "Kanan → Kiri",
            ),
            selected = state.komikReadingDirection,
            onSelect = vm::setKomikReadingDirection,
        )
        Text(
            text = stringResource(R.string.settings_manga_quality, state.mangaImageQuality),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChipRow(
            options = listOf(
                "data_saver" to "Data Saver",
                "full" to "Full Quality",
            ),
            selected = state.mangaImageQuality,
            onSelect = vm::setMangaImageQuality,
        )
        // Translate
        Text(
            text = stringResource(R.string.settings_translate_lang, state.defaultTranslateLanguage),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        ChipRow(
            options = listOf(
                "id" to "Indonesia",
                "en" to "English",
                "ms" to "Melayu",
                "ja" to "日本語",
                "zh" to "中文",
                "ko" to "한국어",
                "ar" to "العربية",
                "hi" to "हिन्दी",
                "es" to "Español",
                "fr" to "Français",
                "de" to "Deutsch",
                "pt" to "Português",
                "ru" to "Русский",
                "th" to "ไทย",
                "vi" to "Tiếng Việt",
            ),
            selected = state.defaultTranslateLanguage,
            onSelect = vm::setDefaultTranslateLanguage,
        )
        LabeledSlider(
            label = stringResource(R.string.settings_translate_chunk, state.clampedTranslateChunk),
            value = state.translateChunkSize.toFloat(),
            onValueChange = { vm.setTranslateChunkSize(it.toInt()) },
            valueRange = 1000f..8000f,
            steps = 13,
        )
        // Library
        Text(
            text = stringResource(R.string.settings_default_sort, state.defaultSort),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChipRow(
            options = listOf(
                "recent" to "Recently Added",
                "title" to "Title A-Z",
                "author" to "Author",
                "progress" to "Reading Progress",
                "last_read" to "Last Read",
                "finished" to "Finished",
            ),
            selected = state.defaultSort,
            onSelect = vm::setDefaultSort,
        )
        ToggleRow(
            title = stringResource(R.string.settings_show_finished),
            checked = state.showFinishedBooks,
            onCheckedChange = vm::setShowFinishedBooks,
        )
        ToggleRow(
            title = stringResource(R.string.settings_auto_download),
            checked = state.autoDownloadNext,
            onCheckedChange = vm::setAutoDownloadNext,
        )
        Text(
            text = stringResource(R.string.settings_import_policy, state.importDuplicatePolicy),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChipRow(
            options = listOf(
                "skip" to "Skip Duplicates",
                "replace" to "Replace",
                "rename" to "Keep Both",
            ),
            selected = state.importDuplicatePolicy,
            onSelect = vm::setImportDuplicatePolicy,
        )
        // Network
        ToggleRow(
            title = stringResource(R.string.settings_wifi_only),
            checked = state.wifiOnlyDownload,
            onCheckedChange = vm::setWifiOnlyDownload,
        )
        LabeledSlider(
            label = stringResource(R.string.settings_cache_size, state.clampedCacheSize),
            value = state.cacheSizeMb.toFloat(),
            onValueChange = { vm.setCacheSizeMb(it.toInt()) },
            valueRange = 64f..2048f,
            steps = 30,
        )
        LabeledSlider(
            label = stringResource(R.string.settings_timeout, state.clampedTimeout),
            value = state.timeoutSeconds.toFloat(),
            onValueChange = { vm.setTimeoutSeconds(it.toInt()) },
            valueRange = 10f..120f,
            steps = 21,
        )
        // Accessibility
        ToggleRow(
            title = stringResource(R.string.settings_high_contrast),
            checked = state.highContrast,
            onCheckedChange = vm::setHighContrast,
        )
        ToggleRow(
            title = stringResource(R.string.settings_larger_touch),
            checked = state.largerTouchTargets,
            onCheckedChange = vm::setLargerTouchTargets,
        )
        ToggleRow(
            title = stringResource(R.string.settings_bold_text),
            checked = state.boldText,
            onCheckedChange = vm::setBoldText,
        )

        // ------------------------------------------------------------------
        // Alat
        // ------------------------------------------------------------------
        SectionHeader(title = stringResource(R.string.settings_tools))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenTranslator)
                .padding(vertical = Sm),
        ) {
            Text(
                text = stringResource(R.string.settings_open_translator),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.settings_translator_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = vm::clearActivity,
            modifier = Modifier.padding(top = Xs),
        ) {
            Text(
                text = stringResource(R.string.action_clear_history),
                color = MaterialTheme.colorScheme.error,
            )
        }

        // ------------------------------------------------------------------
        // Tentang
        // ------------------------------------------------------------------
        SectionHeader(title = stringResource(R.string.settings_about))
        val aboutContext = LocalContext.current
        val logoBitmap = remember(aboutContext) {
            Logo.drawable(aboutContext)?.toBitmap()?.asImageBitmap()
        }
        if (logoBitmap != null) {
            Image(
                bitmap = logoBitmap,
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(72.dp),
            )
        }
        Text(
            text = stringResource(R.string.settings_about_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.settings_about_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.settings_about_sources),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.settings_about_credits),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = stringResource(R.string.manga_credit),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.settings_version),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = SectionGap),
        )
    }
}

@Composable
private fun CustomAccentRow(
    selectedArgb: Long?,
    onSelect: (Long?) -> Unit,
) {
    val swatches = listOf(
        null to stringResource(R.string.settings_accent_preset),
        0xFFA34A21L to "Clay",
        0xFF2F5D50L to "Pine",
        0xFF33477AL to "Indigo",
        0xFF6E3358L to "Plum",
        0xFF1565C0L to "Ocean",
        0xFF2E7D32L to "Forest",
        0xFFC62828L to "Ruby",
        0xFF6A1B9AL to "Violet",
        0xFFEF6C00L to "Amber",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        swatches.forEach { (argb, label) ->
            val selected = selectedArgb == argb
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (argb == null) MaterialTheme.colorScheme.surfaceVariant
                        else Color(argb),
                    )
                    .clickable { onSelect(argb) }
                    .padding(all = if (selected) 14.dp else 10.dp),
            ) {
                if (argb == null) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (selected) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
    )
}

@Composable
private fun ChipRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Sm),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
            )
        }
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(end = Md),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private val Xs = 4.dp
