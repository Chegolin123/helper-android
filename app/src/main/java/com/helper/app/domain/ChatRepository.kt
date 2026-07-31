package com.helper.app.domain

import com.helper.app.data.local.ChatStore
import com.helper.app.data.model.ChatMessage
import com.helper.app.data.remote.ApiResult
import com.helper.app.data.remote.DeepSeekApi

/**
 * Оркестрирует сетевой слой и хранилище. UI работает только с этим репозиторием.
 */
class ChatRepository(
    private val api: DeepSeekApi = DeepSeekApi(),
    private val store: ChatStore,
) {
    /** Загрузить сохранённую историю (для старта экрана). */
    fun loadHistory(): List<ChatMessage> = store.load()

    /** Отправить запрос в DeepSeek. История для контекста = текущий список сообщений. */
    suspend fun ask(history: List<ChatMessage>): ApiResult {
        // Контекст для API — только user/assistant (system подставляет сам api).
        val context = history.filter { it.role != ChatMessage.ROLE_SYSTEM }
        return api.complete(context)
    }

    /** Сохранить текущий диалог. */
    fun persist(messages: List<ChatMessage>) {
        store.save(messages)
    }

    /** Полная очистка истории. */
    fun clear() {
        store.clear()
    }
}
