package com.helper.app.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.helper.app.data.local.ReminderStore

/**
 * Ловит срабатывание напоминания (Фича 1): показывает уведомление
 * и удаляет сработавшее напоминание из хранилища.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val (id, text) = ReminderScheduler.extractReminder(intent)
        if (id < 0) return

        // Удаляем сработавшее из хранилища.
        val store = ReminderStore(context)
        store.remove(id)

        // Показываем уведомление.
        Notifications.ensureChannels(context)
        Notifications.showReminder(context, id, text)
    }
}
