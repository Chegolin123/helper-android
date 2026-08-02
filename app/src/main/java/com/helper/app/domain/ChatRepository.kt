package com.helper.app.domain

import com.helper.app.data.local.ChatStore
import com.helper.app.data.model.ChatMessage
import com.helper.app.data.remote.ApiResult
import com.helper.app.data.remote.DeepSeekApi
import kotlinx.coroutines.flow.Flow

/**
 * Оркестрирует сетевой слой и хранилище. UI работает только с этим репозиторием.
 */
class ChatRepository(
    private val api: DeepSeekApi = DeepSeekApi(),
    private val store: ChatStore,
) {
    /** Загрузить сохранённую историю (для старта экрана). */
    fun loadHistory(): List<ChatMessage> = store.load()

    /**
     * Отправить запрос. Если задан [sessionId] — сервер сам ведёт контекст (фича 4),
     * иначе шлём всю историю.
     */
    suspend fun ask(
        history: List<ChatMessage>,
        sessionId: String? = null,
        stream: Boolean = false,
    ): ApiResult {
        val context = history.filter { it.role != ChatMessage.ROLE_SYSTEM }
        return api.complete(context, sessionId = sessionId, stream = stream)
    }

    /** Стриминговый вариант (фича 3). */
    fun askStream(
        history: List<ChatMessage>,
        sessionId: String? = null,
    ): Flow<String> {
        val context = history.filter { it.role != ChatMessage.ROLE_SYSTEM }
        return api.completeStream(context, sessionId = sessionId)
    }

    /** Создать новую серверную сессию (фича 4). Возвращает session_id или null. */
    suspend fun createSession(): String? = api.createSession()

    /** Очистить серверную сессию (фича 4). */
    suspend fun clearSession(sessionId: String) {
        api.deleteSession(sessionId)
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
