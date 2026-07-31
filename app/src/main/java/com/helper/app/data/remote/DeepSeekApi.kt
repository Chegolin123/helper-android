package com.helper.app.data.remote

import com.helper.app.BuildConfig
import com.helper.app.data.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Результат сетевого вызова: либо текст ответа, либо понятная ошибка.
 */
sealed interface ApiResult {
    data class Success(val content: String) : ApiResult
    data class Error(val message: String, val code: Int? = null) : ApiResult
}

/**
 * Тонкий клиент DeepSeek Chat API. Без сторонних HTTP-библиотек — только
 * HttpURLConnection + org.json, чтобы не раздувать APK.
 */
class DeepSeekApi(
    private val baseUrl: String = BuildConfig.DEEPSEEK_BASE_URL,
    private val apiKey: String = BuildConfig.DEEPSEEK_API_KEY,
    private val model: String = BuildConfig.DEEPSEEK_MODEL,
) {
    /**
     * Системный промпт Саши — личного ИИ-секретаря Алексея.
     */
    private val systemPrompt: String =
        "Ты — Саша, личный ИИ-секретарь Алексея. " +
            "Отвечай коротко, по делу, без воды. " +
            "Если уместно — структурируй ответ списками. " +
            "Обращайся к собеседнику на «ты»."

    /** Отправляет историю диалога и возвращает ответ ассистента. */
    suspend fun complete(history: List<ChatMessage>): ApiResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext ApiResult.Error("Ключ API не задан в local.properties (DEEPSEEK_API_KEY)")
        }
        var conn: HttpURLConnection? = null
        try {
            conn = (URL("$baseUrl/chat/completions").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer $apiKey")
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
            }

            val messages = JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", systemPrompt))
                history.forEach { m ->
                    put(JSONObject().put("role", m.role).put("content", m.content))
                }
            }

            val body = JSONObject()
                .put("model", model)
                .put("messages", messages)
                .put("max_tokens", MAX_TOKENS)
                .put("temperature", TEMPERATURE)
                .toString()

            conn.outputStream.use { os: OutputStream -> os.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val payload = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""

            if (code in 200..299) {
                val content = JSONObject(payload)
                    .getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content")
                ApiResult.Success(content.trim())
            } else {
                ApiResult.Error(message = errorMessage(payload, code), code = code)
            }
        } catch (e: Exception) {
            ApiResult.Error(message = "Сеть недоступна: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            conn?.disconnect()
        }
    }

    private fun errorMessage(payload: String, code: Int): String {
        val parsed = runCatching {
            JSONObject(payload).optJSONObject("error")?.optString("message")
        }.getOrNull()
        return parsed?.takeIf { it.isNotBlank() } ?: "Ошибка API ($code)"
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 30_000
        const val READ_TIMEOUT_MS = 60_000
        const val MAX_TOKENS = 1000
        const val TEMPERATURE = 0.5
    }
}
