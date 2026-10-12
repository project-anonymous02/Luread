package com.luread.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luread.app.R
import com.luread.app.core.design.LureadSpace.Lg
import com.luread.app.core.design.LureadSpace.Md
import com.luread.app.core.design.LureadSpace.SectionGap
import com.luread.app.core.design.LureadSpace.Sm
import com.luread.app.core.design.LureadTopBar
import com.luread.app.core.design.SectionHeader
import com.luread.app.core.design.StateMessage

/**
 * Penerjemah berbasis `Intent.ACTION_PROCESS_TEXT`.
 *
 * Luread sengaja tidak membawa mesin terjemahan atau model ML apa pun. Sistem
 * Android menyediakan intent standar ini dan aplikasi penerjemah yang sudah
 * terpasang akan muncul di pemilih. Hasil diterima kembali lewat
 * `EXTRA_PROCESS_TEXT` pada activity result, lalu ditampilkan untuk disalin.
 *
 * Ketersediaan bergantung pada perangkat: bila tidak ada aplikasi yang
 * menangani intent ini, layar menyatakan demikian secara jujur.
 */
@Composable
fun TranslatorScreen(initialText: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf(initialText.orEmpty()) }
    var result by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("") }

    val handlerCount = remember(context) { countTranslateHandlers(context) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { activityResult ->
        val translated = activityResult.data
            ?.getStringExtra(Intent.EXTRA_PROCESS_TEXT)
            ?: activityResult.data?.getStringExtra("android.intent.extra.PROCESS_TEXT")
        if (!translated.isNullOrBlank()) {
            result = translated
            status = ""
        } else if (activityResult.resultCode == Activity.RESULT_CANCELED && result.isEmpty()) {
            status = context.getString(R.string.translator_cancelled)
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
                text = stringResource(R.string.translator_intro),
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

            if (handlerCount == 0) {
                StateMessage(
                    title = stringResource(R.string.translator_no_app_title),
                    subtitle = stringResource(R.string.translator_no_app_body),
                )
            } else {
                Button(
                    onClick = {
                        if (text.isBlank()) {
                            status = context.getString(R.string.translator_enter_text)
                            return@Button
                        }
                        val intent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                            putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                        }
                        val chooser = Intent.createChooser(intent, context.getString(R.string.translator_title))
                        try {
                            launcher.launch(chooser)
                        } catch (e: ActivityNotFoundException) {
                            status = context.getString(R.string.translator_none_available)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.translator_action))
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
                        .padding(bottom = Xs),
                )
                OutlinedButton(
                    onClick = { copyToClipboard(context, result) },
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

private val Xs = 4.dp

private fun countTranslateHandlers(context: Context): Int {
    val intent = Intent(Intent.ACTION_PROCESS_TEXT).apply { type = "text/plain" }
    return if (Build.VERSION.SDK_INT >= 33) {
        context.packageManager.queryIntentActivities(
            intent,
            android.content.pm.PackageManager.ResolveInfoFlags.of(0),
        ).size
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.queryIntentActivities(intent, 0).size
    }
}

private fun copyToClipboard(context: Context, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Luread", value))
}
