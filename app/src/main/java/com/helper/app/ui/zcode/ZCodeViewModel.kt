package com.helper.app.ui.zcode

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.helper.app.data.local.HarnessStore
import com.helper.app.data.remote.ZCodeApi
import com.helper.app.data.remote.ZCodeEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Роль сообщения в ленте харнесса. */
enum class ZcRole { USER, ASSISTANT, TOOL, STATUS }

data class ZcMsg(
    val id: String,
    val role: ZcRole,
    val text: String,
)

data class ZCodeUiState(
    val tokenSet: Boolean = false,
    val projects: List<String> = emptyList(),
    val project: String? = null,
    val loadingProjects: Boolean = false,
    val messages: List<ZcMsg> = emptyList(),
    val input: String = "",
    val running: Boolean = false,
    val error: String? = null,
)

/**
 * Харнесс: чат с headless ZCode (ZCode Protocol через мост).
 * Сессия создаётся лениво при первом сообщении в выбранном проекте.
 */
class ZCodeViewModel(
    private val store: HarnessStore,
    private val api: ZCodeApi = ZCodeApi(),
) : ViewModel() {

    private val _ui = MutableStateFlow(ZCodeUiState(tokenSet = store.token != null))
    val ui: StateFlow<ZCodeUiState> = _ui.asStateFlow()

    private var sessionId: String? = null
    private var streamJob: Job? = null
    private val token get() = store.token

    init {
        if (_ui.value.tokenSet) loadProjects()
    }

    /** Сохранить токен моста и загрузить проекты. */
    fun setToken(value: String) {
        store.token = value.trim()
        _ui.update { it.copy(tokenSet = value.isNotBlank(), error = null) }
        if (value.isNotBlank()) loadProjects()
    }

    fun loadProjects() {
        val t = token ?: return
        _ui.update { it.copy(loadingProjects = true, error = null) }
        viewModelScope.launch {
            val res = api.projects(t)
            _ui.update { st ->
                res.fold(
                    onSuccess = { names ->
                        val keep = st.project?.takeIf { it in names }
                        st.copy(loadingProjects = false, projects = names, project = keep ?: names.firstOrNull())
                    },
                    onFailure = { e ->
                        st.copy(loadingProjects = false, error = "Не удалось получить проекты: ${e.message}")
                    },
                )
            }
        }
    }

    fun selectProject(name: String) {
        if (name == _ui.value.project) return
        sessionId = null
        _ui.update { it.copy(project = name, messages = emptyList(), error = null) }
    }

    fun onInputChange(text: String) {
        _ui.update { it.copy(input = text) }
    }

    /** Отправить задачу агенту (создаёт сессию при первом запросе). */
    fun send() {
        val text = _ui.value.input.trim()
        val project = _ui.value.project ?: return
        if (text.isEmpty() || _ui.value.running) return
        val t = token ?: return

        _ui.update { st ->
            st.copy(
                messages = st.messages + ZcMsg(newId(), ZcRole.USER, text),
                input = "",
                error = null,
            )
        }

        viewModelScope.launch {
            try {
                if (sessionId == null) {
                    sessionId = api.createSession(t, project).getOrThrow()
                }
                val sid = sessionId!!
                val sendResult = api.send(t, sid, text)
                sendResult.getOrThrow()

                _ui.update { it.copy(running = true) }
                streamJob?.cancel()
                streamJob = viewModelScope.launch {
                    api.stream(t, sid).collect { ev -> onEvent(ev) }
                }
            } catch (e: Exception) {
                _ui.update { st ->
                    st.copy(
                        running = false,
                        messages = st.messages + ZcMsg(newId(), ZcRole.STATUS, "⚠️ ${e.message ?: "Сетевая ошибка"}"),
                    )
                }
            }
        }
    }

    /** Остановить генерацию (как Ctrl+C в TUI). */
    fun stop() {
        val sid = sessionId ?: return
        val t = token ?: return
        streamJob?.cancel()
        viewModelScope.launch {
            api.stop(t, sid)
            _ui.update { it.copy(running = false) }
        }
    }

    private fun onEvent(ev: ZCodeEvent) {
        when (ev) {
            is ZCodeEvent.Delta -> {
                _ui.update { st ->
                    val msgs = st.messages.toMutableList()
                    val last = msgs.lastOrNull()
                    if (last != null && last.role == ZcRole.ASSISTANT) {
                        msgs[msgs.size - 1] = last.copy(text = last.text + ev.text)
                    } else {
                        msgs += ZcMsg(newId(), ZcRole.ASSISTANT, ev.text)
                    }
                    st.copy(messages = msgs)
                }
            }
            is ZCodeEvent.Tool -> {
                _ui.update { it.copy(messages = it.messages + ZcMsg(newId(), ZcRole.TOOL, ev.label)) }
            }
            is ZCodeEvent.Status -> _ui.update { it.copy(running = ev.running) }
            is ZCodeEvent.Done -> {
                _ui.update { st -> st.copy(
                    running = false,
                    messages = if (ev.ok) st.messages
                    else st.messages + ZcMsg(newId(), ZcRole.STATUS, "⚠️ ${ev.error ?: "Ход завершился ошибкой"}"),
                ) }
            }
        }
    }

    private fun newId(): String = "zc-${System.currentTimeMillis()}-${counter++}"
    private var counter = 0L

    companion object {
        /** Фабрика с контекстом (как ChatViewModel.factory). */
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ZCodeViewModel(HarnessStore.get(context.applicationContext)) as T
            }
        }
    }
}