package com.helper.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.helper.app.data.update.UpdateCheckResult
import com.helper.app.data.update.UpdateChecker
import com.helper.app.ui.assistant.AssistantScreen
import com.helper.app.ui.chat.ChatScreen
import com.helper.app.ui.chat.UpdateDialog
import com.helper.app.ui.theme.HelperTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ассистент ходит на HTTPS-домен; в некоторых сетях IPv6-попытка
        // не откатывается на IPv4 и валит соединение.
        System.setProperty("java.net.preferIPv4Stack", "true")
        enableEdgeToEdge()
        setContent {
            HelperTheme {
                HomeTabs()
            }
        }
    }
}

/** Две вкладки: ассистент на DSH и старый чат с Сашей. */
@Composable
private fun HomeTabs() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val updateChecker = remember { UpdateChecker() }
    var update by remember { mutableStateOf<UpdateCheckResult?>(null) }
    LaunchedEffect(Unit) {
        update = updateChecker.check()
    }
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text("Ассистент") },
                icon = { Icon(Icons.Filled.SmartToy, contentDescription = null) },
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text("Саша") },
                icon = { Icon(Icons.Filled.ChatBubble, contentDescription = null) },
            )
        }
        Box(modifier = Modifier.fillMaxSize()) {
            when (tab) {
                0 -> AssistantScreen()
                1 -> ChatScreen()
            }
        }
    }

    // Проверка обновлений — на старте, независимо от выбранной вкладки.
    (update as? UpdateCheckResult.Available)?.let { available ->
        UpdateDialog(state = available, onDismiss = { update = null })
    }
}
