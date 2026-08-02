package com.helper.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.helper.app.data.local.ChatStore
import com.helper.app.data.model.ChatMessage
import com.helper.app.data.remote.ApiResult
import com.helper.app.domain.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Состояние экрана чата. */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isStreaming: Boolean = false,
    val lastError: String? = null,
    val input: String = "",
    val streamEnabled: Boolean = true,
) {
    /** Готовый плоский список с разделителями дат и пометками таймстампов. */
    val items: List<ChatItem> get() = buildChatItems(messages)

    val isEmpty: Boolean get() = messages.isEmpty() && !isLoading
}

/**
 * Управляет логикой чата: отправка, стриминг, серверный контекст, очистка.
 */
class ChatViewModel(
    private val repo: ChatRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(ChatUiState(messages = repo.loadHistory()))
    val ui: StateFlow<ChatUiState> = _ui.asStateFlow()

    /** Серверная сессия (фича 4). Лениво создаём при первом запросе. */
    private var sessionId: String? = null
    private var sessionInitStarted = false

    private val streamEnabled = true

    fun onInputChange(text: String) {
        _ui.update { it.copy(input = text) }
    }

    fun toggleStream() {
        _ui.update { it.copy(streamEnabled = !it.streamEnabled) }
    }

    /** Отправить текущий ввод в API (со стримингом или без). */
    fun send() {
        val text = _ui.value.input.trim()
        if (text.isEmpty() || _ui.value.isLoading || _ui.value.isStreaming) return

        val userMsg = ChatMessage.user(text)
        val updated = _ui.value.messages + userMsg
        _ui.update { it.copy(messages = updated, input = "", isLoading = true, lastError = null) }
        persist(updated)

        viewModelScope.launch {
            // Гарантируем наличие серверной сессии.
            ensureSession()

            if (_ui.value.streamEnabled) {
                // Фича 3: стриминг — сразу добавляем пустой assistant-пузырь и растим его.
                val replyId = "a-stream-${System.currentTimeMillis()}"
                _ui.update { it.copy(messages = it.messages + ChatMessage.assistant("", 0).copy(id = replyId), isStreaming = true) }
                val sb = StringBuilder()
                repo.askStream(updated, sessionId).collect { delta ->
                    sb.append(delta)
                    _ui.update { st ->
                        val msgs = st.messages.map {
                            if (it.id == replyId) it.copy(content = sb.toString(), timestamp = System.currentTimeMillis())
                            else it
                        }
                        st.copy(messages = msgs)
                    }
                }
                val finalText = sb.toString()
                if (finalText.startsWith("[Ошибка") || finalText.startsWith("[Ошибк")) {
                    _ui.update { it.copy(isStreaming = false, isLoading = false, lastError = finalText) }
                } else {
                    val finalMsg = ChatMessage.assistant(finalText)
                    _ui.update { it.copy(
                        messages = it.messages.map { m -> if (m.id == replyId) finalMsg else m },
                        isStreaming = false, isLoading = false,
                    ) }
                    persist(_ui.value.messages)
                }
            } else {
                when (val res = repo.ask(updated, sessionId)) {
                    is ApiResult.Success -> {
                        val reply = ChatMessage.assistant(res.content)
                        val withReply = _ui.value.messages + reply
                        _ui.update { it.copy(messages = withReply, isLoading = false) }
                        persist(withReply)
                    }
                    is ApiResult.Error -> {
                        _ui.update { it.copy(isLoading = false, lastError = res.message) }
                    }
                }
            }
        }
    }

    /** Очистить диалог: локально + серверную сессию. */
    fun clearChat() {
        val sid = sessionId
        if (sid != null) {
            viewModelScope.launch { repo.clearSession(sid) }
        }
        sessionId = null
        sessionInitStarted = false
        repo.clear()
        _ui.update { it.copy(messages = emptyList(), lastError = null) }
    }

    /** Повторить после ошибки (user уже в истории, без дубля). */
    fun retry() {
        val current = _ui.value
        if (current.isLoading || current.isStreaming) return
        if (current.messages.lastOrNull { it.isUser } == null) return
        _ui.update { it.copy(isLoading = true, lastError = null) }
        viewModelScope.launch {
            ensureSession()
            if (_ui.value.streamEnabled) {
                val replyId = "a-retry-${System.currentTimeMillis()}"
                _ui.update { it.copy(messages = it.messages + ChatMessage.assistant("", 0).copy(id = replyId), isStreaming = true) }
                val sb = StringBuilder()
                repo.askStream(current.messages, sessionId).collect { delta ->
                    sb.append(delta)
                    _ui.update { st ->
                        st.copy(messages = st.messages.map {
                            if (it.id == replyId) it.copy(content = sb.toString(), timestamp = System.currentTimeMillis())
                            else it
                        })
                    }
                }
                val finalText = sb.toString()
                val finalMsg = ChatMessage.assistant(finalText)
                _ui.update { it.copy(
                    messages = it.messages.map { m -> if (m.id == replyId) finalMsg else m },
                    isStreaming = false, isLoading = false,
                ) }
                persist(_ui.value.messages)
            } else {
                when (val res = repo.ask(current.messages, sessionId)) {
                    is ApiResult.Success -> {
                        val reply = ChatMessage.assistant(res.content)
                        val withReply = _ui.value.messages + reply
                        _ui.update { it.copy(messages = withReply, isLoading = false) }
                        persist(withReply)
                    }
                    is ApiResult.Error -> _ui.update { it.copy(isLoading = false, lastError = res.message) }
                }
            }
        }
    }

    /** Лениво создаёт серверную сессию при первом запросе (фича 4). */
    private suspend fun ensureSession() {
        if (sessionId != null || sessionInitStarted) return
        sessionInitStarted = true
        sessionId = repo.createSession()
    }

    private fun persist(messages: List<ChatMessage>) {
        repo.persist(messages)
    }

    /** Фабрика для создания ViewModel с зависимостями. */
    class Factory(private val store: ChatStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repo = ChatRepository(store = store)
            return ChatViewModel(repo) as T
        }
    }

    companion object {
        /** Создать фабрику, подставив ChatStore с реальным контекстом приложения. */
        fun factory(context: android.content.Context): Factory =
            Factory(ChatStore(context))
    }
}
