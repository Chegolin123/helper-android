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
import kotlinx.coroutines.delay
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
    private var streamTarget = StringBuilder()
    private var visibleChars = 0
    private var typewriterJob: Job? = null
    private var pendingTurn: DshEvent.TurnEnd? = null

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
        resetTypewriter()
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
                abortTyping(error.message ?: "Не удалось отправить")
            }
        }
    }

    fun clearChat() {
        streamJob?.cancel()
        streamJob = null
        resetTypewriter()
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
        resetTypewriter()
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
                resetTypewriter()
                currentAssistantId = null
                val history = DshApi.parseHistory(event.records)
                if (history.isNotEmpty()) {
                    _ui.update { it.copy(messages = history) }
                    store.saveMessages(history)
                }
            }
            is DshEvent.TextDelta -> appendDelta(event.text)
            is DshEvent.AssistantFinal -> finishAssistant(event.text)
            is DshEvent.TurnEnd -> onTurnEnd(event)
            is DshEvent.Title -> _ui.update { it.copy(title = event.title) }
            is DshEvent.Status -> _ui.update { it.copy(status = event.text) }
            is DshEvent.Error -> {
                val message = event.message
                if (message.contains("401") || message.contains("auth", true) || message.contains("Сессия")) {
                    handleAuth(message)
                } else {
                    abortTyping(message)
                }
            }
            DshEvent.Closed -> {
                if (_ui.value.isStreaming) {
                    abortTyping("Соединение с DSH закрыто")
                }
            }
        }
    }

    private fun appendDelta(delta: String) {
        if (delta.isEmpty()) return
        ensureAssistantBubble()
        streamTarget.append(delta)
        ensureTypewriter()
    }

    private fun finishAssistant(text: String) {
        if (text.isBlank()) return
        ensureAssistantBubble()
        streamTarget = StringBuilder(text)
        visibleChars = minOf(visibleChars, text.length)
        updateAssistantContent(streamTarget.substring(0, visibleChars))
        ensureTypewriter()
    }

    private fun ensureAssistantBubble(): String {
        val existing = currentAssistantId
        if (existing != null && _ui.value.messages.any { it.id == existing }) return existing
        val id = existing ?: "a-${UUID.randomUUID()}"
        currentAssistantId = id
        _ui.update { state ->
            if (state.messages.any { it.id == id }) state
            else state.copy(
                messages = state.messages + ChatMessage(
                    id = id,
                    role = ChatMessage.ROLE_ASSISTANT,
                    content = "",
                    timestamp = System.currentTimeMillis(),
                ),
            )
        }
        return id
    }

    private fun ensureTypewriter() {
        if (typewriterJob?.isActive == true) return
        if (visibleChars >= streamTarget.length) {
            pendingTurn?.let { turn ->
                pendingTurn = null
                completeTurn(turn)
            }
            return
        }
        typewriterJob = viewModelScope.launch {
            try {
                while (visibleChars < streamTarget.length) {
                    visibleChars = minOf(streamTarget.length, visibleChars + 2)
                    updateAssistantContent(streamTarget.substring(0, visibleChars))
                    delay(16)
                }
            } finally {
                typewriterJob = null
            }
            if (visibleChars < streamTarget.length) {
                ensureTypewriter()
            } else {
                pendingTurn?.let { turn ->
                    pendingTurn = null
                    completeTurn(turn)
                }
            }
        }
    }

    private fun updateAssistantContent(text: String) {
        val id = currentAssistantId ?: ensureAssistantBubble()
        _ui.update { state ->
            state.copy(
                messages = state.messages.map {
                    if (it.id == id) {
                        it.copy(content = text, timestamp = System.currentTimeMillis())
                    } else it
                },
            )
        }
    }

    private fun onTurnEnd(event: DshEvent.TurnEnd) {
        if (typewriterJob?.isActive == true) {
            pendingTurn = event
            _ui.update { it.copy(status = null) }
        } else {
            completeTurn(event)
        }
    }

    private fun completeTurn(event: DshEvent.TurnEnd) {
        _ui.update {
            it.copy(
                isStreaming = false,
                status = null,
                error = if (event.completed) it.error else (event.error ?: "Ход завершился ошибкой"),
            )
        }
        store.saveMessages(_ui.value.messages)
        currentAssistantId = null
        streamTarget = StringBuilder()
        visibleChars = 0
    }

    private fun abortTyping(message: String) {
        typewriterJob?.cancel()
        typewriterJob = null
        val id = currentAssistantId
        val full = streamTarget.toString()
        if (full.isBlank()) {
            if (id != null) {
                _ui.update { state ->
                    state.copy(messages = state.messages.filterNot { it.id == id })
                }
            }
        } else {
            updateAssistantContent(full)
        }
        _ui.update { it.copy(isStreaming = false, status = null, error = message) }
        store.saveMessages(_ui.value.messages)
        currentAssistantId = null
        streamTarget = StringBuilder()
        visibleChars = 0
        pendingTurn = null
    }

    private fun resetTypewriter() {
        typewriterJob?.cancel()
        typewriterJob = null
        streamTarget = StringBuilder()
        visibleChars = 0
        pendingTurn = null
    }

    private fun handleAuth(message: String) {
        streamJob?.cancel()
        streamJob = null
        resetTypewriter()
        api.updateToken(null)
        store.clearToken()
        val friendly = if (message.contains("401") || message.contains("101")) {
            "Сессия истекла — войди заново"
        } else {
            message
        }
        _ui.update {
            it.copy(
                needsLogin = true,
                loginLoading = false,
                loginError = friendly,
                isStreaming = false,
                status = null,
            )
        }
    }

    override fun onCleared() {
        streamJob?.cancel()
        typewriterJob?.cancel()
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
