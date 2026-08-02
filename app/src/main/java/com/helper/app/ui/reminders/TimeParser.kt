package com.helper.app.ui.reminders

import java.util.concurrent.TimeUnit

/**
 * Парсер человекочитаемого времени для напоминаний (Фича 1).
 * Поддерживает: «30м», «30 минут», «2ч», «2 часа», «1д», «1 день», «в 18:30».
 */
object TimeParser {

    /** Возвращает (epochMillis, ошибка|null). */
    fun parse(text: String): Long? {
        val t = text.trim().lowercase()
        if (t.isEmpty()) return null

        // Абсолютное время «в 18:30» или «18:30».
        val abs = Regex("""(?:в\s+)?(\d{1,2}):(\d{2})""").find(t)
        if (abs != null) {
            val h = abs.groupValues[1].toInt()
            val m = abs.groupValues[2].toInt()
            if (h in 0..23 && m in 0..59) {
                return absoluteTime(h, m)
            }
        }

        // Относительное: «30 минут», «2ч 15м», «1 день».
        var totalMs = 0L
        var matched = false

        val minPat = Regex("""(\d+)\s*(м|мин|минут|минуту|минуты|минута)""")
        val hourPat = Regex("""(\d+)\s*(ч|час|часа|часов)""")
        val dayPat = Regex("""(\d+)\s*(д|день|дня|дней|суток|сутки)""")

        minPat.findAll(t).forEach { totalMs += it.groupValues[1].toLong() * TimeUnit.MINUTES.toMillis(1); matched = true }
        hourPat.findAll(t).forEach { totalMs += it.groupValues[1].toLong() * TimeUnit.HOURS.toMillis(1); matched = true }
        dayPat.findAll(t).forEach { totalMs += it.groupValues[1].toLong() * TimeUnit.DAYS.toMillis(1); matched = true }

        return if (matched) System.currentTimeMillis() + totalMs else null
    }

    private fun absoluteTime(hour: Int, minute: Int): Long {
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }
}
