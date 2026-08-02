package com.helper.app.ui.chat

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.util.Locale

/**
 * Фича 5: голосовой ввод (STT) через системный SpeechRecognizer.
 * Открывает системный диалог распознавания, результат — в onText.
 */
@Composable
fun rememberVoiceInputLauncher(
    onText: (String) -> Unit,
    onUnavailable: () -> Unit,
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val text = matches?.firstOrNull()
            if (!text.isNullOrBlank()) {
                onText(text)
            }
        }
    }

    return remember(launcher) {
        {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("ru", "RU").toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите…")
            }
            try {
                launcher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                onUnavailable()
            }
        }
    }
}
