package com.helper.app.scheduling

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.helper.app.MainActivity
import com.helper.app.R

/** Утилита для показа уведомлений (Фичи 1 и 6). */
object Notifications {

    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_SUMMARY = "daily_summary"
    private const val NOTIF_ID_REMINDER_BASE = 1000
    const val NOTIF_ID_SUMMARY = 500

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_REMINDERS, "Напоминания",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = "Срабатывание напоминаний" }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SUMMARY, "Утреннее резюме",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Ежедневная сводка в 10:00" }
            )
        }
    }

    fun showReminder(context: Context, reminderId: Int, text: String) {
        show(
            context = context,
            channel = CHANNEL_REMINDERS,
            id = NOTIF_ID_REMINDER_BASE + reminderId,
            title = "⏰ Напоминание",
            message = text,
        )
    }

    fun showSummary(context: Context, summary: String) {
        show(
            context = context,
            channel = CHANNEL_SUMMARY,
            id = NOTIF_ID_SUMMARY,
            title = "🌅 Утреннее резюме",
            message = summary,
        )
    }

    private fun show(context: Context, channel: String, id: Int, title: String, message: String) {
        try {
            val intent = Intent(context, MainActivity::class.java)
            val pi = PendingIntent.getActivity(
                context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_helper_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS не выдано — молча пропускаем.
        }
    }
}
