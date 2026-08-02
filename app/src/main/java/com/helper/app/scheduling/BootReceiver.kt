package com.helper.app.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Восстанавливает напоминания и утреннее резюме после перезагрузки телефона
 * (Фичи 1 и 6): AlarmManager сбрасывается при ребуте.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Notifications.ensureChannels(context)
            ReminderScheduler.rescheduleAll(context)
            DailySummaryScheduler.scheduleNext(context)
        }
    }
}
