package com.helper.app.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.helper.app.data.local.ReminderStore
import com.helper.app.data.model.Reminder

/**
 * Планирует срабатывание напоминаний через AlarmManager (Фича 1).
 * setAlarmClock — самый надёжный режим для точного времени на Android 12+.
 */
object ReminderScheduler {

    private const val ACTION_REMINDER = "com.helper.app.action.REMINDER"
    private const val EXTRA_ID = "reminder_id"
    private const val EXTRA_TEXT = "reminder_text"

    fun schedule(context: Context, reminder: Reminder) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = reminderPendingIntent(context, reminder)

        try {
            am.setAlarmClock(
                AlarmManager.AlarmClockInfo(reminder.dueEpoch, null),
                pi,
            )
        } catch (e: Exception) {
            // Если exact alarm запрещён — fallback на inexact.
            am.set(AlarmManager.RTC_WAKEUP, reminder.dueEpoch, pi)
        }
    }

    fun cancel(context: Context, reminder: Reminder) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(reminderPendingIntent(context, reminder))
    }

    /** Перепланировать все активные (после ребута, фича 1+6). */
    fun rescheduleAll(context: Context) {
        val store = ReminderStore(context)
        store.load().forEach { schedule(context, it) }
    }

    private fun reminderPendingIntent(context: Context, reminder: Reminder): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            putExtra(EXTRA_ID, reminder.id)
            putExtra(EXTRA_TEXT, reminder.text)
        }
        return PendingIntent.getBroadcast(
            context,
            reminder.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Распарсить вспомогательные данные из intent (для receiver). */
    fun extractReminder(intent: Intent): Pair<Int, String> {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        val text = intent.getStringExtra(EXTRA_TEXT) ?: ""
        return id to text
    }
}
