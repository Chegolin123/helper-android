package com.helper.app.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.helper.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Фича 6: при срабатывании в 10:00 запрашивает /v1/daily_summary на сервере
 * Саши и показывает уведомление. Планирует следующий день.
 */
class DailySummaryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.helper.app.action.DAILY_SUMMARY") return

        // Планируем следующий день сразу.
        if (DailySummaryScheduler.isEnabled(context)) {
            DailySummaryScheduler.scheduleNext(context)
        }

        CoroutineScope(Dispatchers.IO).launch {
            val summary = fetchSummary()
            withContext(Dispatchers.Main) {
                Notifications.ensureChannels(context)
                if (summary != null) {
                    Notifications.showSummary(context, summary)
                }
            }
        }
    }

    private suspend fun fetchSummary(): String? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL("${BuildConfig.DEEPSEEK_BASE_URL}/daily_summary").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer ${BuildConfig.DEEPSEEK_API_KEY}")
                connectTimeout = 20_000
                readTimeout = 60_000
            }
            if (conn.responseCode in 200..299) {
                val payload = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(payload).optString("summary").takeIf { it.isNotBlank() }
            } else null
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
