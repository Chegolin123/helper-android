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
    val lastError: String? = null,
    val input: String = "",
) {
    /** Готовый плоский список с разделителями дат и пометками таймстампов. */
    val items: List<ChatItem> get() = buildChatItems(messages)

    val isEmpty: Boolean get() = messages.isEmpty() && !isLoading
}

/**
 * Управляет логикой чата: отправка, сохранение истории, очистка.
 * Не зависит от UI — тестируется отдельно.
 */
class ChatViewModel(
    private val repo: ChatRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(ChatUiState(messages = repo.loadHistory()))
    val ui: StateFlow<ChatUiState> = _ui.asStateFlow()

    fun onInputChange(text: String) {
        _ui.update { it.copy(input = text) }
    }

    /** Отправить текущий ввод в API. */
    fun send() {
        val text = _ui.value.input.trim()
        if (text.isEmpty() || _ui.value.isLoading) return

        val userMsg = ChatMessage.user(text)
        val updated = _ui.value.messages + userMsg
        _ui.update { it.copy(messages = updated, input = "", isLoading = true, lastError = null) }
        persist(updated)

        viewModelScope.launch {
            when (val res = repo.ask(updated)) {
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

    /** Очистить весь диалог (с подтверждением в UI). */
    fun clearChat() {
        repo.clear()
        _ui.update { it.copy(messages = emptyList(), lastError = null) }
    }

    /**
     * Повторить запрос после ошибки. User-сообщение уже в истории, поэтому НЕ
     * добавляем дубль — просто заново шлём историю в API.
     */
    fun retry() {
        val current = _ui.value
        if (current.isLoading) return
        if (current.messages.lastOrNull { it.isUser } == null) return
        _ui.update { it.copy(isLoading = true, lastError = null) }
        viewModelScope.launch {
            when (val res = repo.ask(current.messages)) {
                is ApiResult.Success -> {
                    val reply = ChatMessage.assistant(res.content)
                    val withReply = current.messages + reply
                    _ui.update { it.copy(messages = withReply, isLoading = false) }
                    persist(withReply)
                }
                is ApiResult.Error -> {
                    _ui.update { it.copy(isLoading = false, lastError = res.message) }
                }
            }
        }
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
