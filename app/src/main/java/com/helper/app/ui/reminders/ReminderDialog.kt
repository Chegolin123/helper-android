package com.helper.app.ui.reminders

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.helper.app.data.local.ReminderStore
import com.helper.app.data.model.Reminder
import com.helper.app.scheduling.Notifications
import com.helper.app.scheduling.ReminderScheduler

/**
 * Диалог создания напоминания (Фича 1).
 * Пример: текст «купить молоко», время «через 30м» / «в 18:30» / «2 часа».
 */
@Composable
fun ReminderDialog(
    onDismiss: () -> Unit,
    onCreated: () -> Unit,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("через 30м") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⏰ Напоминание") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Что напомнить?") },
                    placeholder = { Text("купить молоко") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("Через сколько?") },
                    placeholder = { Text("через 30м / в 18:30 / 2 часа") },
                    singleLine = true,
                )
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val t = text.trim()
                    if (t.isEmpty()) {
                        error = "Напиши, что напомнить"
                        return@TextButton
                    }
                    val due = TimeParser.parse(time)
                    if (due == null) {
                        error = "Не поняла время. Примеры: «через 30м», «в 18:30», «2 часа»"
                        return@TextButton
                    }
                    createReminder(context, t, due)
                    onCreated()
                },
            ) { Text("Создать") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

private fun createReminder(context: Context, text: String, dueEpoch: Long) {
    val store = ReminderStore(context)
    val reminder = Reminder(id = store.nextId(), text = text, dueEpoch = dueEpoch)
    store.add(reminder)
    ReminderScheduler.schedule(context, reminder)
    Notifications.ensureChannels(context)
}
