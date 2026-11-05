package com.helper.app.ui.assistant

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.helper.app.data.model.ChatMessage
import com.helper.app.ui.chat.components.MarkdownText
import com.helper.app.ui.chat.components.TypingDots

/**
 * Нативный чат с DSH. Вход — пароль remote-mobile, дальше сообщения идут
 * в серверную сессию через HTTP/WebSocket, без WebView и веб-интерфейса.
 */
@Composable
fun AssistantScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val vm: DshChatViewModel = viewModel(factory = DshChatViewModel.factory(context))
    val state by vm.ui.collectAsStateWithLifecycle()

    if (state.needsLogin) {
        LoginPane(
            loading = state.loginLoading,
            error = state.loginError,
            onLogin = vm::login,
        )
    } else {
        ChatPane(
            state = state,
            onInputChange = vm::onInputChange,
            onSend = vm::send,
            onNewChat = vm::clearChat,
            onLogout = vm::logout,
        )
    }
}

@Composable
private fun LoginPane(
    loading: Boolean,
    error: String?,
    onLogin: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.SmartToy,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(16.dp))
        Text(
            text = "Ассистент",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "assistant.nexgate.ru · вход в DSH",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(24.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Пароль") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onLogin(password) }),
        )
        if (!error.isNullOrBlank()) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
        Spacer(Modifier.size(16.dp))
        Button(
            onClick = { onLogin(password) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading && password.isNotBlank(),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Войти")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatPane(
    state: DshUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onNewChat: () -> Unit,
    onLogout: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content?.length) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title ?: "Ассистент") },
                actions = {
                    IconButton(onClick = onNewChat) {
                        Icon(Icons.Filled.Add, contentDescription = "Новый чат")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Выйти")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.messages.isEmpty()) {
                    item {
                        Text(
                            text = "Напиши сообщение — ответ придёт сюда, без веб-интерфейса DSH.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                items(state.messages, key = { it.id }) { message ->
                    MessageRow(
                        message = message,
                        streaming = state.isStreaming && message.id == state.messages.lastOrNull()?.id,
                    )
                }
            }

            state.status?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            state.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Сообщение ассистенту") },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                )
                Spacer(Modifier.size(4.dp))
                IconButton(
                    onClick = onSend,
                    enabled = state.input.isNotBlank() && !state.isStreaming,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
                }
            }
        }
    }
}

@Composable
private fun MessageRow(message: ChatMessage, streaming: Boolean) {
    val isUser = message.isUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isUser) {
            Icon(
                imageVector = Icons.Filled.SmartToy,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(8.dp))
        }
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .widthIn(max = 300.dp)
                .animateContentSize(),
        ) {
            if (isUser) {
                Text(
                    text = message.content,
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            } else if (message.content.isBlank() && streaming) {
                TypingDots(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    dotColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                MarkdownText(
                    markdown = message.content,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    baseColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
