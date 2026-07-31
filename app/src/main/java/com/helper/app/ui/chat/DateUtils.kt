package com.helper.app.ui.chat

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Форматирование дат/времени для UI чата.
 * Чистая утилита без Android-зависимостей — легко тестировать.
 */
object DateUtils {

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dayFmt = SimpleDateFormat("d MMMM", Locale("ru"))

    /** «12:06» — время сообщения. */
    fun formatTime(ts: Long): String = timeFmt.format(Date(ts))

    /**
     * Человекочитаемый разделитель даты:
     *  - сегодня → «Сегодня»
     *  - вчера   → «Вчера»
     *  - в этом году → «31 июля»
     *  - прошлые годы → «31 июля 2025»
     */
    fun formatDayDivider(ts: Long, now: Long = System.currentTimeMillis()): String {
        val dayDiff = dayDifference(ts, now)
        return when {
            dayDiff == 0L -> "Сегодня"
            dayDiff == 1L -> "Вчера"
            sameYear(ts, now) -> dayFmt.format(Date(ts))
            else -> dayFmt.format(Date(ts)) + " " + yearOf(ts)
        }
    }

    /**
     * Достаточно ли отличается день, чтобы показать таймстамп под сообщением.
     * Используется для группировки: если у соседних сообщений разница > порога,
     * таймстамп показываем, иначе прячем (чтобы не шуметь).
     */
    fun shouldShowTimestamp(currentTs: Long, prevTs: Long?): Boolean {
        if (prevTs == null) return true
        val gap = currentTs - prevTs
        return gap >= TimeUnit.MINUTES.toMillis(3)
    }

    private fun dayDifference(a: Long, b: Long): Long {
        val calA = Calendar.getInstance().apply { timeInMillis = a ; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val calB = Calendar.getInstance().apply { timeInMillis = b ; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        return TimeUnit.MILLISECONDS.toDays(calB.timeInMillis - calA.timeInMillis)
    }

    private fun sameYear(a: Long, b: Long): Boolean =
        yearOf(a) == yearOf(b)

    private fun yearOf(ts: Long): String =
        SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(ts))
}
