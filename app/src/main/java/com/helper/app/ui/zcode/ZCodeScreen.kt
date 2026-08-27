package com.helper.app.ui.zcode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Харнесс: чат с headless ZCode.
 * Без токена — экран ввода; дальше выбор проекта, чат со стримингом и кнопка «Стоп».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZCodeScreen() {
    val context = LocalContext.current
    val vm: ZCodeViewModel = viewModel(factory = ZCodeViewModel.factory(context))
    val state by vm.ui.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    if (!state.tokenSet) {
        TokenSetup(onSave = vm::setToken)
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.padding(end = 10.dp))
                        Column {
                            Text("ZCode", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (state.running) "работает…" else "headless-агент",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (state.running) {
                        IconButton(onClick = vm::stop) {
                            Icon(Icons.Outlined.Stop, contentDescription = "Остановить", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ProjectSelector(
                projects = state.projects,
                selected = state.project,
                loading = state.loadingProjects,
                canChange = !state.running,
                onSelect = vm::selectProject,
            )
            val err = state.error
            if (err != null) {
                Text(
                    text = err,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (state.messages.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Filled.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.padding(8.dp))
                        Text("Поставь задачу агенту", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Проект: ${state.project ?: "—"} · агент работает в клоне на Коренёво",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.messages, key = { it.id }) { msg ->
                            when (msg.role) {
                                ZcRole.USER -> ZcBubble(msg.text, isUser = true)
                                ZcRole.ASSISTANT -> ZcBubble(msg.text, isUser = false)
                                ZcRole.TOOL -> ZcMeta(msg.text)
                                ZcRole.STATUS -> ZcMeta(msg.text)
                            }
                        }
                    }
                }
            }

            ZcInput(
                text = state.input,
                running = state.running,
                onTextChange = vm::onInputChange,
                onSend = vm::send,
                onStop = vm::stop,
            )
        }
    }
}

/** Выбор проекта (клон на сервере). */
@Composable
private fun ProjectSelector(
    projects: List<String>,
    selected: String?,
    loading: Boolean,
    canChange: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Проект:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.padding(end = 8.dp))
        OutlinedButton(
            onClick = { expanded = true },
            enabled = canChange,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.padding(end = 6.dp))
            }
            Text(selected ?: "—", fontWeight = FontWeight.Medium)
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            projects.forEach { p ->
                DropdownMenuItem(text = { Text(p) }, onClick = {
                    expanded = false
                    onSelect(p)
                })
            }
        }
    }
}

/** Баббл сообщения (справа — пользователь, слева — агент). */
@Composable
private fun ZcBubble(text: String, isUser: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp).let {
                if (isUser) it.copy(topEnd = CornerSize(6.dp))
                else it.copy(topStart = CornerSize(6.dp))
            },
            color = if (isUser) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Мелкая строка: шаг инструмента или статус. */
@Composable
private fun ZcMeta(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = FontFamily.Monospace,
    )
}

/** Ввод задачи + кнопка «Стоп» во время работы агента. */
@Composable
private fun ZcInput(
    text: String,
    running: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val canSend = text.isNotBlank() && !running
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .navigationBarsPadding()
            .imePadding(),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f).widthIn(min = 0.dp),
            placeholder = { Text(if (running) "ZCode работает…" else "Задача для агента…") },
            enabled = !running,
            maxLines = 5,
            shape = RoundedCornerShape(24.dp),
            keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
        )
        Spacer(Modifier.padding(end = 8.dp))
        if (running) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(bottom = 4.dp).size(48.dp),
            ) {
                IconButton(onClick = onStop) {
                    Icon(Icons.Outlined.Stop, contentDescription = "Остановить")
                }
            }
        } else {
            Surface(
                shape = CircleShape,
                color = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp).size(48.dp),
            ) {
                IconButton(onClick = onSend, enabled = canSend) {
                    Icon(Icons.Outlined.Send, contentDescription = "Отправить")
                }
            }
        }
    }
}

/** Первый запуск: ввод токена моста. */
@Composable
private fun TokenSetup(onSave: (String) -> Unit) {
    var token by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Code,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.padding(12.dp))
        Text("Подключение к ZCode", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.padding(6.dp))
        Text(
            "Введи токен моста (выдаётся при настройке харнесса). Хранится в шифрованном хранилище устройства.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.padding(12.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Токен") },
            singleLine = true,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.padding(8.dp))
        TextButton(onClick = { onSave(token) }, enabled = token.isNotBlank()) {
            Text("Подключиться")
        }
    }
}