package com.helper.app.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.helper.app.data.update.ApkInstaller
import com.helper.app.data.update.UpdateCheckResult
import com.helper.app.ui.chat.components.MarkdownText
import kotlinx.coroutines.launch

/**
 * Диалог обновления. Показывает версию, заметки релиза и кнопку «Обновить».
 * Скачивание — с индикатором прогресса.
 */
@Composable
fun UpdateDialog(
    state: UpdateCheckResult.Available,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val installer = remember { ApkInstaller(context) }

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text("Доступна версия v${state.versionName}") },
        text = {
            Column {
                Text(
                    text = "Что нового:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 8.dp),
                ) {
                    MarkdownText(markdown = state.notes)
                }
                if (downloading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    )
                }
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !downloading,
                onClick = {
                    downloading = true
                    error = null
                    scope.launch {
                        when (val r = installer.downloadAndInstall(state.apkUrl)) {
                            is ApkInstaller.InstallResult.Started -> {
                                // Системный установщик открылся — закрываем диалог.
                                downloading = false
                                onDismiss()
                            }
                            is ApkInstaller.InstallResult.Error -> {
                                downloading = false
                                error = r.message
                            }
                        }
                    }
                },
            ) { Text("Обновить") }
        },
        dismissButton = {
            TextButton(
                enabled = !downloading,
                onClick = onDismiss,
            ) { Text("Позже") }
        },
    )
}
