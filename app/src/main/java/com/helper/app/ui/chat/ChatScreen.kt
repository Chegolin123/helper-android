package com.helper.app.ui.chat

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.helper.app.data.location.LocationProvider
import com.helper.app.data.update.UpdateCheckResult
import com.helper.app.data.update.UpdateChecker
import com.helper.app.scheduling.DailySummaryScheduler
import com.helper.app.ui.chat.components.DayDivider
import com.helper.app.ui.chat.components.EmptyState
import com.helper.app.ui.chat.components.MessageBubble
import com.helper.app.ui.chat.components.SashaAvatar
import com.helper.app.ui.chat.components.TypingDots
import com.helper.app.ui.reminders.ReminderDialog
import kotlinx.coroutines.launch

private val Suggestions = listOf(
    "Что ты умеешь?",
    "Напиши план на день",
    "Объясни простыми словами",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    val context = LocalContext.current
    val vm: ChatViewModel = viewModel(factory = ChatViewModel.factory(context))
    val state by vm.ui.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    var showClearDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }

    // Проверка обновления при старте (один раз).
    val updateChecker = remember { UpdateChecker() }
    var update by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var updateRequested by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!updateRequested) {
            updateRequested = true
            update = updateChecker.check()
        }
    }

    // Фича 5: голосовой ввод (STT). Требует RECORD_AUDIO.
    val scope = rememberCoroutineScope()
    val voiceLauncher = rememberVoiceInputLauncher(
        onText = { vm.onInputChange(it) },
        onUnavailable = {
            scope.launch { snackbar.showSnackbar("Распознавание речи недоступно на устройстве") }
        },
    )
    val recordAudioPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) voiceLauncher() else scope.launch { snackbar.showSnackbar("Нужен доступ к микрофону") } }
    val launchVoice: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voiceLauncher()
        } else {
            recordAudioPerm.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    // Фича 2: геолокация. Требует ACCESS_COARSE_LOCATION.
    val locationProvider = remember { LocationProvider(context) }
    val fetchLocationAndSend = {
        scope.launch {
            val loc = locationProvider.getLocationString()
            if (loc != null) {
                vm.onInputChange("📍 Я сейчас здесь: $loc. ")
                vm.send()
            } else {
                snackbar.showSnackbar("Не удалось получить геолокацию")
            }
        }
    }
    val locationPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) fetchLocationAndSend() else scope.launch { snackbar.showSnackbar("Нужен доступ к геолокации") }
    }
    val launchLocation: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchLocationAndSend()
        } else {
            locationPerm.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    // Показываем ошибку как снекбар с действием «Повторить».
    LaunchedEffect(state.lastError) {
        val err = state.lastError ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = err,
            actionLabel = "Повторить",
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) {
            vm.retry()
        }
    }

    // Авто-скролл к последнему сообщению.
    LaunchedEffect(state.items.size, state.isLoading) {
        if (state.items.isNotEmpty()) {
            listState.animateScrollToItem(state.items.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SashaAvatar(size = 32)
                        Spacer(Modifier.padding(end = 10.dp))
                        Column {
                            Text("Саша", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (state.isLoading) "печатает…" else "ИИ-секретарь",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    // Фича 1: создать напоминание.
                    IconButton(onClick = { showReminderDialog = true }) {
                        Icon(Icons.Outlined.Alarm, contentDescription = "Напоминание")
                    }
                    // Фича 6: включить/выключить утреннее резюме.
                    val summaryOn = DailySummaryScheduler.isEnabled(context)
                    IconButton(
                        onClick = {
                            DailySummaryScheduler.setEnabled(context, !summaryOn)
                            if (!summaryOn) {
                                scope.launch { snackbar.showSnackbar("Утреннее резюме включено (10:00)") }
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (summaryOn) Icons.Filled.WbSunny else Icons.Outlined.WbSunny,
                            contentDescription = "Утреннее резюме",
                            tint = if (summaryOn) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.messages.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Outlined.CleaningServices, contentDescription = "Очистить чат")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (state.isEmpty) {
                    EmptyState(
                        suggestions = Suggestions,
                        onSuggestion = { vm.onInputChange(it); vm.send() },
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(state.items, key = { it.key }) { item ->
                            when (item) {
                                is ChatItem.DayDivider -> DayDivider(label = item.label)
                                is ChatItem.MessageItem -> MessageBubble(
                                    message = item.message,
                                    timeLabel = DateUtils.formatTime(item.message.timestamp),
                                    showTime = item.showTime,
                                )
                            }
                        }
                        if (state.isLoading) {
                            item(key = "typing") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Start,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    SashaAvatar(size = 28)
                                    Spacer(Modifier.padding(end = 8.dp))
                                    TypingDots(dotColor = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }

            ChatInput(
                text = state.input,
                isLoading = state.isLoading,
                onTextChange = vm::onInputChange,
                onSend = vm::send,
                onVoice = { launchVoice() },
                onLocation = { launchLocation() },
            )
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Очистить чат?") },
            text = { Text("Вся история диалога будет удалена без возможности восстановления.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearChat()
                    showClearDialog = false
                }) { Text("Очистить") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Отмена") }
            },
        )
    }

    // Диалог самообновления, если нашли свежий релиз.
    val available = update as? UpdateCheckResult.Available
    if (available != null) {
        UpdateDialog(
            state = available,
            onDismiss = { update = null },
        )
    }

    // Фича 1: диалог создания напоминания.
    if (showReminderDialog) {
        ReminderDialog(
            onDismiss = { showReminderDialog = false },
            onCreated = {
                showReminderDialog = false
                scope.launch { snackbar.showSnackbar("⏰ Напоминание создано") }
            },
        )
    }
}

@Composable
private fun ChatInput(
    text: String,
    isLoading: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoice: () -> Unit,
    onLocation: () -> Unit,
) {
    val canSend = text.isNotBlank() && !isLoading
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .navigationBarsPadding()
            .imePadding(),
        verticalAlignment = Alignment.Bottom,
    ) {
        // Кнопка геолокации (Фича 2).
        IconButton(
            onClick = onLocation,
            enabled = !isLoading,
        ) {
            Icon(
                imageVector = Icons.Outlined.MyLocation,
                contentDescription = "Отправить геолокацию",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Кнопка голосового ввода (Фича 5).
        IconButton(
            onClick = onVoice,
            enabled = !isLoading,
        ) {
            Icon(
                imageVector = Icons.Outlined.Mic,
                contentDescription = "Голосовой ввод",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .weight(1f)
                .widthIn(min = 0.dp),
            placeholder = { Text("Спроси Сашу…") },
            enabled = !isLoading,
            maxLines = 5,
            shape = RoundedCornerShape(24.dp),
            // Отправка по Enter на физической/IME-клавиатуре (гейт Efficiency).
            keyboardActions = KeyboardActions(
                onSend = { if (canSend) onSend() },
            ),
            keyboardOptions = KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Send,
            ),
        )
        Spacer(Modifier.padding(end = 8.dp))
        // Круглая заливочная кнопка: активна — primary, отключена — приглушённая.
        // Явный visual feedback состояния (гейт Primary action + Component family).
        Surface(
            shape = CircleShape,
            color = if (canSend) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (canSend) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(bottom = 4.dp)
                .size(48.dp),
        ) {
            IconButton(
                onClick = onSend,
                enabled = canSend,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Send,
                    contentDescription = "Отправить",
                )
            }
        }
    }
}
