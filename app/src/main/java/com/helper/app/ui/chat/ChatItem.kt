package com.helper.app.ui.chat

import com.helper.app.data.model.ChatMessage

/**
 * Элемент плоского списка чата. Либо сообщение, либо разделитель даты.
 * Разделители вставляются при смене календарного дня между сообщениями.
 */
sealed interface ChatItem {
    val key: String

    data class MessageItem(
        val message: ChatMessage,
        val showTime: Boolean,
    ) : ChatItem {
        override val key: String get() = message.id
    }

    data class DayDivider(
        val label: String,
        val dayStartTs: Long,
    ) : ChatItem {
        override val key: String get() = "divider-$dayStartTs"
    }
}

/**
 * Превращает исходный список сообщений в плоский список с разделителями дат и
 * пометками, у каких сообщений показывать таймстамп (группировка по времени).
 */
fun buildChatItems(
    messages: List<ChatMessage>,
    now: Long = System.currentTimeMillis(),
): List<ChatItem> {
    if (messages.isEmpty()) return emptyList()
    val out = ArrayList<ChatItem>(messages.size + 4)

    // Граница дня для вставки разделителей.
    var lastDayBoundary: Long? = null
    var prevTs: Long? = null

    for (m in messages) {
        // Разделитель при смене календарного дня.
        if (dayBoundary(m.timestamp) != lastDayBoundary) {
            lastDayBoundary = dayBoundary(m.timestamp)
            out.add(ChatItem.DayDivider(DateUtils.formatDayDivider(m.timestamp, now), lastDayBoundary!!))
        }
        val showTime = DateUtils.shouldShowTimestamp(m.timestamp, prevTs)
        out.add(ChatItem.MessageItem(m, showTime))
        prevTs = m.timestamp
    }
    return out
}

/** Начало календарного дня для [ts] в epoch millis. */
private fun dayBoundary(ts: Long): Long {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = ts
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
