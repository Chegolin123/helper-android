package com.helper.app.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import java.util.Calendar

/**
 * Планирует утреннее резюме на 10:00 каждый день (Фича 6).
 * AlarmManager.setAlarmClock — точный и переживает Doze.
 */
object DailySummaryScheduler {

    private const val ACTION_SUMMARY = "com.helper.app.action.DAILY_SUMMARY"
    private const val REQUEST_CODE = 42
    private const val PREFS = "helper_daily"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) scheduleNext(context) else cancel(context)
    }

    /** Планирует срабатывание на ближайшее 10:00 (сегодня, если ещё не прошло). */
    fun scheduleNext(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context)

        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
        }

        try {
            am.setAlarmClock(
                AlarmManager.AlarmClockInfo(cal.timeInMillis, null),
                pi,
            )
        } catch (e: Exception) {
            am.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, DailySummaryReceiver::class.java).apply {
            action = ACTION_SUMMARY
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
