package com.helper.app.ui.chat.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.helper.app.data.model.ChatMessage
import com.helper.app.ui.theme.BubbleAssistant
import com.helper.app.ui.theme.BubbleUser

private val BubbleShape = RoundedCornerShape(18.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    timeLabel: String,
    showTime: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isUser = message.isUser
    val alignment = if (isUser) Arrangement.End else Arrangement.Start
    val bubbleColor = if (isUser) BubbleUser else BubbleAssistant
    val onColor = if (isUser) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface
    val roleLabel = if (isUser) "Вы" else "Саша"

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = alignment,
            verticalAlignment = Alignment.Bottom,
        ) {
            if (!isUser) {
                SashaAvatar(size = 28)
                Spacer(Modifier.padding(end = 8.dp))
            }

            Surface(
                color = bubbleColor,
                shape = BubbleShape,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .semantics { contentDescription = "$roleLabel: ${message.content}. Долгое нажатие копирует" }
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            copyToClipboard(context, message.content)
                            Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                        },
                    ),
            ) {
                if (isUser) {
                    Text(
                        text = message.content,
                        color = onColor,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                } else {
                    MarkdownText(
                        markdown = message.content,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        baseColor = onColor,
                    )
                }
            }
        }

        // Таймстамп показываем только при группировке (первые в группе / смена автора/времени).
        if (showTime) {
            Spacer(Modifier.padding(top = 3.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            ) {
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = if (isUser) 0.dp else 36.dp,
                        end = if (isUser) 4.dp else 0.dp,
                    ),
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("helper", text))
}
