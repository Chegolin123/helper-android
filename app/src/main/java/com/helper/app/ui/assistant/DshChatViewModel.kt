package com.helper.app.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.helper.app.data.dsh.DshApi
import com.helper.app.data.dsh.DshAuthException
import com.helper.app.data.dsh.DshEvent
import com.helper.app.data.dsh.DshStore
import com.helper.app.data.model.ChatMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class DshUiState(
    val needsLogin: Boolean = true,
    val loginLoading: Boolean = false,
    val loginError: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isStreaming: Boolean = false,
    val status: String? = null,
    val error: String? = null,
    val title: String? = null,
)

/**
 * Нативный чат с DSH: сессия живёт на сервере, приложение показывает
 * историю и поток текста, не открывая веб-интерфейс.
 */
class DshChatViewModel(
    private val store: DshStore,
) : ViewModel() {

    private val api = DshApi(store.baseUrl, store.token)
    private val _ui = MutableStateFlow(
        DshUiState(
            needsLogin = store.token.isNullOrBlank(),
            messages = store.loadMessages(),
        ),
    )
    val ui: StateFlow<DshUiState> = _ui.asStateFlow()

    private var sessionId: String? = store.sessionId
    private var streamJob: Job? = null
    private var currentAssistantId: String? = null

    init {
        if (!store.token.isNullOrBlank()) {
            start()
        }
    }

    fun onInputChange(text: String) {
        _ui.update { it.copy(input = text) }
    }

    fun login(password: String) {
        if (password.isBlank() || _ui.value.loginLoading) return
        _ui.update { it.copy(loginLoading = true, loginError = null) }
        viewModelScope.launch {
            api.login(password)
                .onSuccess { issued ->
                    store.token = issued
                    _ui.update {
                        it.copy(
                            needsLogin = false,
                            loginLoading = false,
                            loginError = null,
                            error = null,
                        )
                    }
                    start()
                }
                .onFailure { error ->
                    _ui.update {
                        it.copy(
                            loginLoading = false,
                            loginError = error.message ?: "Не удалось войти",
                        )
                    }
                }
        }
    }

    fun send() {
        val text = _ui.value.input.trim()
        if (text.isEmpty() || _ui.value.isStreaming || _ui.value.needsLogin) return
        val now = System.currentTimeMillis()
        val assistantId = "a-${UUID.randomUUID()}"
        currentAssistantId = assistantId

        _ui.update {
            it.copy(
                messages = it.messages +
                    ChatMessage.user(text, now) +
                    ChatMessage(assistantId, ChatMessage.ROLE_ASSISTANT, "", now),
                input = "",
                isStreaming = true,
                status = "Отправляю…",
                error = null,
            )
        }
        store.saveMessages(_ui.value.messages)

        viewModelScope.launch {
            try {
                ensureSession()
                api.prompt(requireNotNull(sessionId), text)
            } catch (error: DshAuthException) {
                handleAuth(error.message ?: "Сессия истекла")
            } catch (error: Exception) {
                _ui.update {
                    it.copy(
                        isStreaming = false,
                        status = null,
                        error = error.message ?: "Не удалось отправить",
                    )
                }
            }
        }
    }

    fun clearChat() {
        streamJob?.cancel()
        streamJob = null
        currentAssistantId = null
        sessionId = null
        store.sessionId = null
        store.saveMessages(emptyList())
        _ui.update {
            it.copy(
                messages = emptyList(),
                input = "",
                isStreaming = false,
                status = null,
                error = null,
                title = null,
            )
        }
        start()
    }

    fun logout() {
        streamJob?.cancel()
        streamJob = null
        currentAssistantId = null
        sessionId = null
        api.updateToken(null)
        store.clearAll()
        _ui.value = DshUiState(needsLogin = true)
    }

    private fun start() {
        viewModelScope.launch {
            try {
                ensureSession()
                openStream()
            } catch (error: DshAuthException) {
                handleAuth(error.message ?: "Нужен вход")
            } catch (error: Exception) {
                _ui.update {
                    it.copy(error = error.message ?: "Не удалось подключиться к DSH")
                }
            }
        }
    }

    private suspend fun ensureSession() {
        if (sessionId != null) return
        val created = api.createSession()
        sessionId = created
        store.sessionId = created
    }

    private fun openStream() {
        streamJob?.cancel()
        val activeSession = sessionId ?: return
        streamJob = viewModelScope.launch {
            api.follow(activeSession).collect { event ->
                handleEvent(event)
            }
        }
    }

    private fun handleEvent(event: DshEvent) {
        when (event) {
            DshEvent.Opened -> _ui.update { it.copy(error = null) }
            is DshEvent.Snapshot -> {
                val history = DshApi.parseHistory(event.records)
                if (history.isNotEmpty()) {
                    _ui.update { it.copy(messages = history) }
                    store.saveMessages(history)
                }
            }
            is DshEvent.TextDelta -> appendDelta(event.text)
            is DshEvent.AssistantFinal -> finishAssistant(event.text)
            is DshEvent.TurnEnd -> {
                _ui.update {
                    it.copy(
                        isStreaming = false,
                        status = null,
                        error = if (event.completed) it.error else (event.error ?: "Ход завершился ошибкой"),
                    )
                }
            }
            is DshEvent.Title -> _ui.update { it.copy(title = event.title) }
            is DshEvent.Status -> _ui.update { it.copy(status = event.text) }
            is DshEvent.Error -> {
                val message = event.message
                if (message.contains("401") || message.contains("auth", true) || message.contains("Сессия")) {
                    handleAuth(message)
                } else {
                    _ui.update {
                        it.copy(isStreaming = false, status = null, error = message)
                    }
                }
            }
            DshEvent.Closed -> _ui.update {
                if (it.isStreaming) {
                    it.copy(
                        isStreaming = false,
                        status = null,
                        error = "Соединение с DSH закрыто",
                    )
                } else it
            }
        }
    }

    private fun appendDelta(delta: String) {
        if (delta.isEmpty()) return
        val id = currentAssistantId ?: "a-${UUID.randomUUID()}".also { currentAssistantId = it }
        _ui.update { state ->
            val hasBubble = state.messages.any { it.id == id }
            val messages = if (hasBubble) {
                state.messages.map {
                    if (it.id == id) {
                        it.copy(
                            content = it.content + delta,
                            timestamp = System.currentTimeMillis(),
                        )
                    } else it
                }
            } else {
                state.messages + ChatMessage(
                    id = id,
                    role = ChatMessage.ROLE_ASSISTANT,
                    content = delta,
                    timestamp = System.currentTimeMillis(),
                )
            }
            state.copy(messages = messages, isStreaming = true)
        }
    }

    private fun finishAssistant(text: String) {
        if (text.isBlank()) return
        val id = currentAssistantId
        _ui.update { state ->
            val messages = if (id != null && state.messages.any { it.id == id }) {
                state.messages.map {
                    if (it.id == id) it.copy(content = text, timestamp = System.currentTimeMillis())
                    else it
                }
            } else {
                state.messages + ChatMessage(
                    id = "a-${UUID.randomUUID()}",
                    role = ChatMessage.ROLE_ASSISTANT,
                    content = text,
                    timestamp = System.currentTimeMillis(),
                )
            }
            state.copy(messages = messages)
        }
        store.saveMessages(_ui.value.messages)
    }

    private fun handleAuth(message: String) {
        streamJob?.cancel()
        streamJob = null
        api.updateToken(null)
        store.clearToken()
        _ui.update {
            it.copy(
                needsLogin = true,
                loginLoading = false,
                loginError = message,
                isStreaming = false,
                status = null,
            )
        }
    }

    override fun onCleared() {
        streamJob?.cancel()
        super.onCleared()
    }

    class Factory(private val store: DshStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DshChatViewModel(store) as T
        }
    }

    companion object {
        fun factory(context: android.content.Context): Factory =
            Factory(DshStore(context.applicationContext))
    }
}
