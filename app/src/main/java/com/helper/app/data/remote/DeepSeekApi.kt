package com.helper.app.data.remote

import com.helper.app.BuildConfig
import com.helper.app.data.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
 * Тонкий клиент чата Саши. Ходит на сервер Саши (VPS), который сам работает
 * с DeepSeek, vault и GitHub. Без сторонних HTTP-библиотек — HttpURLConnection.
 *
 * Поддерживает:
 *  - сессии (фича 4): передаём session_id, сервер хранит контекст
 *  - SSE-стриминг (фича 3): completeStream() отдаёт дельты по мере генерации
 */
class DeepSeekApi(
    private val baseUrl: String = BuildConfig.DEEPSEEK_BASE_URL,
    private val apiKey: String = BuildConfig.DEEPSEEK_API_KEY,
    private val model: String = BuildConfig.DEEPSEEK_MODEL,
) {
    /**
     * Отправляет запрос и возвращает полный ответ ассистента.
     * @param sessionId если не null — сервер читает контекст сессии сам.
     * @param stream если true — сервер вернёт SSE; здесь собираем весь ответ.
     */
    suspend fun complete(
        history: List<ChatMessage>,
        sessionId: String? = null,
        stream: Boolean = false,
    ): ApiResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext ApiResult.Error("Ключ API не задан в local.properties (DEEPSEEK_API_KEY)")
        }
        var conn: HttpURLConnection? = null
        try {
            conn = openConnection(stream)
            conn.outputStream.use { os: OutputStream ->
                os.write(buildBody(history, sessionId, stream).toByteArray(Charsets.UTF_8))
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                val payload = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                return@withContext ApiResult.Error(errorMessage(payload, code), code)
            }

            if (stream) {
                // Собираем весь стрим в один текст.
                val sb = StringBuilder()
                conn.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (line.startsWith("data: ") && !line.contains("[DONE]")) {
                            runCatching {
                                val d = JSONObject(line.removePrefix("data: "))
                                val delta = d.getJSONArray("choices").getJSONObject(0)
                                    .optJSONObject("delta")
                                delta?.optString("content")?.let { sb.append(it) }
                            }
                        }
                    }
                }
                ApiResult.Success(sb.toString().trim())
            } else {
                val payload = conn.inputStream.bufferedReader().use { it.readText() }
                val content = JSONObject(payload)
                    .getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content")
                ApiResult.Success(content.trim())
            }
        } catch (e: Exception) {
            ApiResult.Error(message = "Сеть недоступна: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * SSE-стриминг: отдаёт дельты текста по мере генерации (фича 3).
     */
    fun completeStream(
        history: List<ChatMessage>,
        sessionId: String? = null,
    ): Flow<String> = flow {
        if (apiKey.isBlank()) {
            emit("[Ошибка: ключ API не задан]")
            return@flow
        }
        var conn: HttpURLConnection? = null
        try {
            conn = openConnection(stream = true)
            conn.outputStream.use { os: OutputStream ->
                os.write(buildBody(history, sessionId, true).toByteArray(Charsets.UTF_8))
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                val payload = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                emit("[Ошибка API ($code): ${errorMessage(payload, code)}]")
                return@flow
            }
            conn.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.startsWith("data: ")) {
                        val data = line.removePrefix("data: ").trim()
                        if (data == "[DONE]") return@useLines
                        runCatching {
                            val d = JSONObject(data)
                            val delta = d.getJSONArray("choices").getJSONObject(0)
                                .optJSONObject("delta")
                            val content = delta?.optString("content")
                            if (!content.isNullOrEmpty()) emit(content)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            emit("[Ошибка сети: ${e.message ?: e.javaClass.simpleName}]")
        } finally {
            conn?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    private fun openConnection(stream: Boolean): HttpURLConnection =
        (URL("$baseUrl/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $apiKey")
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
        }

    /** Собирает JSON-тело запроса. Без локального system-промпта — его задаёт сервер. */
    private fun buildBody(history: List<ChatMessage>, sessionId: String?, stream: Boolean): String {
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", MAX_TOKENS)
            .put("temperature", TEMPERATURE)
            .put("stream", stream)
        if (sessionId != null) {
            // Фича 4: сервер читает контекст сессии сам; шлём только последний user-запрос.
            val lastUser = history.lastOrNull { it.isUser }?.content ?: ""
            body.put("session_id", sessionId).put("query", lastUser)
        } else {
            val messages = JSONArray().apply {
                history.forEach { m ->
                    put(JSONObject().put("role", m.role).put("content", m.content))
                }
            }
            body.put("messages", messages)
        }
        return body.toString()
    }

    private fun errorMessage(payload: String, code: Int): String {
        val parsed = runCatching {
            JSONObject(payload).optJSONObject("error")?.optString("message")
        }.getOrNull()
        return parsed?.takeIf { it.isNotBlank() } ?: "Ошибка API ($code)"
    }

    /** Создать серверную сессию (фича 4). */
    suspend fun createSession(): String? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL("$baseUrl/sessions").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $apiKey")
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            val payload = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            JSONObject(payload).optString("session_id").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    /** Очистить серверную сессию (фича 4). */
    suspend fun deleteSession(sessionId: String) {
        withContext(Dispatchers.IO) {
            try {
                val conn = (URL("$baseUrl/sessions/$sessionId").openConnection() as HttpURLConnection).apply {
                    requestMethod = "DELETE"
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    connectTimeout = 15_000
                    readTimeout = 15_000
                }
                conn.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 30_000
        const val READ_TIMEOUT_MS = 60_000
        const val MAX_TOKENS = 1000
        const val TEMPERATURE = 0.5
    }
}
