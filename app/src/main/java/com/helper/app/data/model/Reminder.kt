package com.helper.app.data.model

/**
 * Напоминание (Фича 1).
 * @param id уникальный (для PendingIntent requestCode).
 * @param text что напомнить.
 * @param dueEpoch когда сработает (epoch millis).
 */
data class Reminder(
    val id: Int,
    val text: String,
    val dueEpoch: Long,
) {
    val isPast: Boolean get() = dueEpoch <= System.currentTimeMillis()
}
