package com.helper.app.data.model

/**
 * Одно сообщение в чате.
 *
 * @param id        стабильный идентификатор (для LazyColumn key).
 * @param role      роль для DeepSeek API: "user" | "assistant" | "system".
 * @param content   текст сообщения.
 * @param timestamp epoch millis.
 */
data class ChatMessage(
    val id: String,
    val role: String,
    val content: String,
    val timestamp: Long,
) {
    val isUser: Boolean get() = role == ROLE_USER

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_SYSTEM = "system"

        fun user(text: String, timestamp: Long = System.currentTimeMillis()): ChatMessage =
            ChatMessage(id = "u-$timestamp", role = ROLE_USER, content = text, timestamp = timestamp)

        fun assistant(text: String, timestamp: Long = System.currentTimeMillis()): ChatMessage =
            ChatMessage(id = "a-$timestamp", role = ROLE_ASSISTANT, content = text, timestamp = timestamp)
    }
}
