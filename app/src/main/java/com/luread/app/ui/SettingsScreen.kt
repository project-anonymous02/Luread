package com.luread.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.asImageBitmap
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
        ChipRow(
            options = AccentPreset.entries.map { it.name to it.label },
            selected = state.accent.name,
            onSelect = { name ->
                AccentPreset.entries.firstOrNull { it.name == name }?.let(vm::setAccent)
            },
        )

        SectionHeader(title = stringResource(R.string.settings_reading))
        Text(
            text = stringResource(R.string.settings_font_size, state.clampedFontSize.toInt()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Slider(
            value = state.fontSizeSp,
            onValueChange = vm::setFontSize,
            valueRange = 14f..30f,
            steps = 15,
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
            text = stringResource(R.string.settings_about_body),
            style = MaterialTheme.typography.bodySmall,
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
