package com.helper.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.helper.app.ui.chat.ChatScreen
import com.helper.app.ui.theme.HelperTheme
import com.helper.app.ui.zcode.ZCodeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Харнесс: мост ZCode доступен по IPv4; в некоторых сетях IPv6-попытка
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

/** Две вкладки: чат с Сашей и харнесс ZCode. */
@Composable
private fun HomeTabs() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text("Саша") },
                icon = { Icon(Icons.Filled.ChatBubble, contentDescription = null) },
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text("ZCode") },
                icon = { Icon(Icons.Filled.Code, contentDescription = null) },
            )
        }
        Box(modifier = Modifier.fillMaxSize()) {
            when (tab) {
                0 -> ChatScreen()
                1 -> ZCodeScreen()
            }
        }
    }
}