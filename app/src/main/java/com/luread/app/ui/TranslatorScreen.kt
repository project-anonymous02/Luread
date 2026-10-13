package com.luread.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luread.app.R
import com.luread.app.core.design.LureadSpace.Lg
import com.luread.app.core.design.LureadSpace.SectionGap
import com.luread.app.core.design.LureadSpace.Sm
import com.luread.app.core.design.LureadTopBar
import com.luread.app.core.design.SectionHeader
import kotlinx.coroutines.launch

/**
 * Penerjemah otomatis in-app.
 *
 * Memakai [com.luread.app.data.translate.TranslationService] yang menjalankan
 * failover antar provider (Google gtx unofficial -> MyMemory) tanpa API key.
 * Bahasa sumber di-auto-detect; bahasa tujuan bisa dipilih (default Indonesia).
 */
@Composable
fun TranslatorScreen(initialText: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as android.app.Application
    val translator = remember { (app as com.luread.app.LureadApp).container.translator }

    var text by rememberSaveable { mutableStateOf(initialText.orEmpty()) }
    var result by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("") }
    var loading by rememberSaveable { mutableStateOf(false) }
    var targetLang by rememberSaveable { mutableStateOf("id") }

    fun doTranslate() {
        if (text.isBlank()) {
            status = context.getString(R.string.translator_enter_text)
            return
        }
        loading = true
        status = ""
        scope.launch {
            when (val outcome = translator.translate(text, "auto", targetLang)) {
                is com.luread.app.data.translate.TranslationService.Outcome.Success -> {
                    result = outcome.result.translatedText
                    status = ""
                }
                is com.luread.app.data.translate.TranslationService.Outcome.Failure -> {
                    status = outcome.message
                }
            }
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LureadTopBar(title = stringResource(R.string.translator_title), onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Lg),
            verticalArrangement = Arrangement.spacedBy(Sm),
        ) {
            Text(
                text = stringResource(R.string.translator_intro_auto),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp),
                placeholder = { Text(stringResource(R.string.translator_placeholder)) },
            )

            // Pilihan bahasa tujuan
            Text(
                text = stringResource(R.string.translator_target_lang),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Sm),
            ) {
                TargetChip("id", "Indonesia", targetLang) { targetLang = it }
                TargetChip("en", "English", targetLang) { targetLang = it }
                TargetChip("ms", "Melayu", targetLang) { targetLang = it }
                TargetChip("ar", "العربية", targetLang) { targetLang = it }
            }

            Button(
                onClick = { doTranslate() },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.heightIn(min = 20.dp))
                } else {
                    Text(stringResource(R.string.translator_action_auto))
                }
            }

            OutlinedButton(
                onClick = {
                    text = ""
                    result = ""
                    status = ""
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.translator_clear))
            }

            if (status.isNotBlank()) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (result.isNotBlank()) {
                SectionHeader(title = stringResource(R.string.translator_result))
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                )
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Luread", result))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_copy))
                }
            }

            Text(
                text = stringResource(R.string.translator_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = SectionGap, bottom = SectionGap),
            )
        }
    }
}

@Composable
private fun TargetChip(code: String, label: String, selected: String, onSelect: (String) -> Unit) {
    androidx.compose.material3.FilterChip(
        selected = selected == code,
        onClick = { onSelect(code) },
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
    )
}
