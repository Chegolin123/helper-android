package com.helper.app.data.dsh

import android.util.Base64
import com.helper.app.data.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.KeyFactory
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

/** События серверной сессии DSH, которые нужны нативному чату. */
sealed interface DshEvent {
    data object Opened : DshEvent
    data class Snapshot(val records: List<JSONObject>) : DshEvent
    data class TextDelta(val text: String) : DshEvent
    data class AssistantFinal(val text: String) : DshEvent
    data class TurnEnd(val completed: Boolean, val error: String?) : DshEvent
    data class Title(val title: String) : DshEvent
    data class Status(val text: String?) : DshEvent
    data class Error(val message: String) : DshEvent
    data object Closed : DshEvent
}

class DshAuthException(message: String) : IOException(message)

/**
 * Нативный клиент DSH: вход через remote-mobile, HTTP RPC и WebSocket-поток.
 *
 * Протокол:
 *  - POST /api/<endpoint>  {type:client-request, rpcId, method, payload:{args:{request}}}
 *  - WS  /api/remote.mux   {type:open, streamId, endpoint, payload}
 */
class DshApi(
    var baseUrl: String,
    var token: String?,
) {
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val wsClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    fun updateToken(value: String?) {
        token = value
    }

    /** Войти по паролю remote-mobile и получить долгоживущий токен. */
    suspend fun login(password: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val authRequest = Request.Builder().url("${baseUrl.trimEnd('/')}/auth").get().build()
            val html = http.newCall(authRequest).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Сервер вернул ${resp.code}")
                resp.body?.string().orEmpty()
            }
            val match = PUBLIC_KEY_RE.find(html)
                ?: throw IOException("На странице входа нет публичного ключа")
            val keyBody = match.groupValues[1]
                .replace("\\n", "")
                .replace("\\r", "")
                .replace(Regex("[^A-Za-z0-9+/=]"), "")
            val keyBytes = Base64.decode(keyBody, Base64.DEFAULT)
            val publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(keyBytes))
            val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                publicKey,
                OAEPParameterSpec(
                    "SHA-256",
                    "MGF1",
                    MGF1ParameterSpec.SHA256,
                    PSource.PSpecified.DEFAULT,
                ),
            )
            val encrypted = Base64.encodeToString(
                cipher.doFinal(password.toByteArray(Charsets.UTF_8)),
                Base64.NO_WRAP,
            )
            val body = JSONObject().put("encryptedCredential", encrypted).toString()
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/remote-mobile/verify")
                .header("Origin", baseUrl)
                .header("Referer", "$baseUrl/")
                .post(body.toRequestBody(JSON))
                .build()
            val response = http.newCall(request).execute()
            val code = response.code
            val text = response.body?.string().orEmpty()
            val setCookie = response.header("Set-Cookie")
            response.close()

            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (code !in 200..299 || !json.optBoolean("success")) {
                val reason = json.optString("reason").ifBlank { "Неверный пароль" }
                throw DshAuthException(reason)
            }
            val issued = json.optString("token").ifBlank {
                setCookie
                    ?.substringAfter("dsh_mobile_token=", "")
                    ?.substringBefore(';')
                    .orEmpty()
            }
            if (issued.isBlank()) throw IOException("Сервер не выдал токен")
            token = issued
            issued
        }
    }

    /** Создать серверную сессию DSH. */
    suspend fun createSession(): String = withContext(Dispatchers.IO) {
        val value = rpc(
            "session/create",
            JSONObject().put("cwd", WORKSPACE),
        )
        value.optString("sessionId").ifBlank {
            throw IOException("DSH не вернул sessionId")
        }
    }

    /** Отправить сообщение в существующую сессию. */
    suspend fun prompt(sessionId: String, text: String) = withContext(Dispatchers.IO) {
        val request = JSONObject()
            .put("requestId", UUID.randomUUID().toString())
            .put("sessionId", sessionId)
            .put("mode", "queue")
            .put(
                "content",
                JSONArray().put(JSONObject().put("type", "text").put("text", text)),
            )
        val value = rpc("session/prompt", request)
        if (!value.optBoolean("accepted")) throw IOException("DSH не принял сообщение")
    }

    /** Открыть поток сессии: историю и живые чанки ответа. */
    fun follow(sessionId: String): Flow<DshEvent> = callbackFlow {
        val current = token
        if (current.isNullOrBlank()) {
            trySend(DshEvent.Error("Нужен вход"))
            close()
            return@callbackFlow
        }
        val request = Request.Builder()
            .url(wsUrl())
            .header("Cookie", "dsh_mobile_token=$current")
            .header("Origin", baseUrl)
            .build()
        val socket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                trySend(DshEvent.Opened)
                val streamId = UUID.randomUUID().toString()
                val followRequest = JSONObject()
                    .put("address", JSONObject().put("kind", "session").put("sessionId", sessionId))
                    .put("assistantStream", true)
                val open = JSONObject()
                    .put("type", "open")
                    .put("streamId", streamId)
                    .put("endpoint", "session/follow")
                    .put("payload", JSONObject().put("args", JSONObject().put("request", followRequest)))
                webSocket.send(open.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseServerMessage(text)?.let { trySend(it) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                trySend(DshEvent.Error(t.message ?: "Соединение с DSH потеряно"))
                close()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                trySend(DshEvent.Closed)
                close()
            }
        })
        awaitClose { socket.close(1000, "leave") }
    }

    private fun rpc(endpoint: String, request: JSONObject): JSONObject {
        val current = token ?: throw DshAuthException("Нужен вход")
        val rpcId = UUID.randomUUID().toString()
        val envelope = JSONObject()
            .put("type", "client-request")
            .put("rpcId", rpcId)
            .put("method", endpoint)
            .put("payload", JSONObject().put("args", JSONObject().put("request", request)))
        val httpRequest = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/$endpoint")
            .header("Cookie", "dsh_mobile_token=$current")
            .header("Origin", baseUrl)
            .header("Referer", "$baseUrl/")
            .post(envelope.toString().toRequestBody(JSON))
            .build()
        http.newCall(httpRequest).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 401) throw DshAuthException("Сессия истекла")
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}: ${text.take(160)}")
            val result = JSONObject(text).optJSONObject("result")
                ?: throw IOException("Некорректный ответ DSH")
            if (!result.optBoolean("ok")) {
                val error = result.optJSONObject("error")
                val code = error?.optString("code").orEmpty()
                val message = error?.optString("message").orEmpty().ifBlank { "Ошибка DSH" }
                if (code.contains("auth", ignoreCase = true) || code.contains("unauthorized", ignoreCase = true)) {
                    throw DshAuthException(message)
                }
                throw IOException(message)
            }
            return result.optJSONObject("value") ?: JSONObject()
        }
    }

    private fun parseServerMessage(text: String): DshEvent? = runCatching {
        val root = JSONObject(text)
        when (root.optString("type")) {
            "item" -> {
                val value = root.optJSONObject("value") ?: return null
                when (value.optString("type")) {
                    "snapshot" -> {
                        val records = value.optJSONArray("records") ?: JSONArray()
                        DshEvent.Snapshot((0 until records.length()).mapNotNull { records.optJSONObject(it) })
                    }
                    "event" -> parseEvent(value.optJSONObject("event"))
                    "assistant-stream" -> parseAssistantFrame(value.optJSONObject("frame"))
                    else -> null
                }
            }
            "error" -> DshEvent.Error(
                root.optJSONObject("error")?.optString("message").orEmpty().ifBlank { "Ошибка соединения" },
            )
            else -> null
        }
    }.getOrNull()

    private fun parseEvent(event: JSONObject?): DshEvent? {
        if (event == null) return null
        val data = event.optJSONObject("data")
        return when (event.optString("type")) {
            "assistant/message" -> {
                val message = data?.optJSONObject("message")
                val content = message?.optJSONArray("content") ?: data?.optJSONArray("content")
                val text = contentText(content)
                if (text.isNotBlank()) DshEvent.AssistantFinal(text) else null
            }
            "turn/end" -> {
                val reason = data?.optJSONObject("reason")
                val kind = reason?.optString("kind").orEmpty()
                DshEvent.TurnEnd(
                    completed = kind == "completed",
                    error = if (kind == "completed" || kind.isBlank()) null else kind,
                )
            }
            "turn/failed" -> DshEvent.TurnEnd(
                completed = false,
                error = data?.optString("error").orEmpty().ifBlank { "Ход завершился ошибкой" },
            )
            "session/title" -> data?.optString("title")?.takeIf { it.isNotBlank() }?.let(DshEvent::Title)
            else -> null
        }
    }

    private fun parseAssistantFrame(frame: JSONObject?): DshEvent? {
        if (frame == null) return null
        return when (frame.optString("type")) {
            "chunk" -> {
                val chunk = frame.optJSONObject("chunk") ?: return null
                when (chunk.optString("type")) {
                    "text-delta" -> DshEvent.TextDelta(chunk.optString("text"))
                    "block-start" -> DshEvent.Status("Печатает…")
                    "finish" -> DshEvent.Status(null)
                    else -> null
                }
            }
            "start" -> DshEvent.Status("Думает…")
            else -> null
        }
    }

    private fun wsUrl(): String {
        val base = baseUrl.trimEnd('/')
        val scheme = when {
            base.startsWith("https://") -> "wss://"
            base.startsWith("http://") -> "ws://"
            else -> "wss://"
        }
        val host = base.removePrefix("https://").removePrefix("http://")
        return "$scheme$host/api/remote.mux"
    }

    companion object {
        private const val WORKSPACE = "/home/assistant/workspace"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val PUBLIC_KEY_RE = Regex(
            "-----BEGIN PUBLIC KEY-----([\\s\\S]*?)-----END PUBLIC KEY-----",
        )

        /** Превратить snapshot-записи follow в сообщения для UI. */
        fun parseHistory(records: List<JSONObject>): List<ChatMessage> {
            val result = mutableListOf<ChatMessage>()
            records.forEachIndexed { index, record ->
                val event = record.optJSONObject("event") ?: record
                val data = event.optJSONObject("data") ?: return@forEachIndexed
                val seq = event.optLong("seq", index.toLong())
                val time = event.optLong("time", 0L).takeIf { it > 0 } ?: System.currentTimeMillis()
                when (event.optString("type")) {
                    "user/message" -> {
                        val source = data.optJSONObject("source")
                        if (data.optString("role") != "user") return@forEachIndexed
                        if (source != null && source.optString("kind") != "user") return@forEachIndexed
                        val text = contentText(data.optJSONArray("content"))
                        if (text.isBlank()) return@forEachIndexed
                        if (text.startsWith("<system-reminder>") ||
                            text.startsWith("Current runtime context") ||
                            text.startsWith("Time sampled while preparing")
                        ) return@forEachIndexed
                        result += ChatMessage(
                            id = "h-u-$seq-$index",
                            role = ChatMessage.ROLE_USER,
                            content = text,
                            timestamp = time,
                        )
                    }
                    "assistant/message" -> {
                        val message = data.optJSONObject("message")
                        val content = message?.optJSONArray("content") ?: data.optJSONArray("content")
                        val text = contentText(content)
                        if (text.isNotBlank()) {
                            result += ChatMessage(
                                id = "h-a-$seq-$index",
                                role = ChatMessage.ROLE_ASSISTANT,
                                content = text,
                                timestamp = time,
                            )
                        }
                    }
                }
            }
            return result
        }

        private fun contentText(content: JSONArray?): String {
            if (content == null) return ""
            val parts = mutableListOf<String>()
            for (i in 0 until content.length()) {
                val part = content.optJSONObject(i) ?: continue
                val text = part.optString("text")
                if (text.isNotBlank()) parts += text
            }
            return parts.joinToString("\n")
        }
    }
}
