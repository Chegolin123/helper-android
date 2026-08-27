package com.helper.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource
import org.json.JSONObject
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Конфигурация харнесса: мост ZCode доступен через nginx на VPS (канал Саши).
 */
object ZCodeConfig {
    val BASE_URL: String = com.helper.app.BuildConfig.ZCODE_BASE_URL
        .ifBlank { "https://server.tail6b33a9.ts.net/zcode" }
    const val PROJECTS_DIR = "/home/finik/zcode-projects"
}

/**
 * Событие живой сессии ZCode (после разбора SSE-потока моста).
 */
sealed interface ZCodeEvent {
    /** Дельты основного текста ответа (kind = text_delta). */
    data class Delta(val text: String) : ZCodeEvent

    /** Информация о действии инструмента/шаге агента. */
    data class Tool(val label: String) : ZCodeEvent

    /** Смена состояния: running / idle. */
    data class Status(val running: Boolean) : ZCodeEvent

    /** Ход завершён (или упал). */
    data class Done(val ok: Boolean, val error: String? = null) : ZCodeEvent
}

/**
 * Клиент моста zcode-bridge (OkHttp).
 *
 * Кастомный DNS принудительно отдаёт IPv4: funnel-домен имеет AAAA, и в сетях
 * без IPv6-маршрута системная попытка v6 не откатывается на v4 (HTTPURLConnection).
 * SNI и Host-заголовок OkHttp берёт из URL, поэтому TLS к tailscale-сертификату
 * продолжает работать.
 *
 * Протокол:
 *  GET  /projects            — список клонов проектов
 *  POST /session  {workspace} — создать сессию → sessionID
 *  POST /send     {session,text}
 *  POST /stop     {session}
 *  GET  /events?session=ID   — SSE: события session/event
 */
class ZCodeApi(
    baseUrl: String = ZCodeConfig.BASE_URL,
) {
    private val base: okhttp3.HttpUrl = baseUrl.toHttpUrl()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            // Funnel-прокси tailscale нестабилен на HTTP/2 — только HTTP/1.1 (как curl).
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            // CLEARTEXT обязателен: мост доступен по http через nginx VPS.
            // (TLS-спека сужена до 1.2 — Conscrypt-хендшейк 1.3 к tailscale-edge сбрасывался.)
            .connectionSpecs(listOf(
                okhttp3.ConnectionSpec.CLEARTEXT,
                okhttp3.ConnectionSpec.Builder(okhttp3.ConnectionSpec.MODERN_TLS)
                    .tlsVersions(okhttp3.TlsVersion.TLS_1_2)
                    .build(),
            ))
            .dns(object : okhttp3.Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    val all = InetAddress.getAllByName(hostname).toList()
                    val v4 = all.filter { it is Inet4Address }
                    return v4.ifEmpty { all }
                }
            })
            .build()
    }

    /** Список проектов на сервере. */
    suspend fun projects(token: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = execute(Request.Builder().url(base.newBuilder().addPathSegment("projects").build())
                .header("Authorization", "Bearer $token").build())
            val names = JSONObject(body).getJSONArray("projects")
            (0 until names.length()).map { names.getString(it) }
        }
    }

    /** Создать сессию в рабочей папке проекта. Возвращает sessionID. */
    suspend fun createSession(token: String, project: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cwd = "${ZCodeConfig.PROJECTS_DIR}/$project"
            val payload = JSONObject().put("workspace", JSONObject()
                .put("workspacePath", cwd)
                .put("workspaceKey", project)).toString()
            val body = execute(Request.Builder().url(base.newBuilder().addPathSegment("session").build())
                .header("Authorization", "Bearer $token")
                .post(payload.toRequestBody(JSON_MEDIA)).build())
            val sid = JSONObject(body).optString("sessionID")
            check(sid.isNotBlank()) { "Сессия не создана: $body" }
            sid
        }
    }

    /** Отправить сообщение агенту. */
    suspend fun send(token: String, sessionId: String, text: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject().put("session", sessionId).put("text", text).toString()
            execute(Request.Builder().url(base.newBuilder().addPathSegment("send").build())
                .header("Authorization", "Bearer $token")
                .post(payload.toRequestBody(JSON_MEDIA)).build())
            Unit
        }
    }

    /** Остановить генерацию. */
    suspend fun stop(token: String, sessionId: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().put("session", sessionId).toString()
                execute(Request.Builder().url(base.newBuilder().addPathSegment("stop").build())
                    .header("Authorization", "Bearer $token")
                    .post(payload.toRequestBody(JSON_MEDIA)).build())
            }
        }
    }

    /**
     * SSE-поток событий сессии. Эмитит [ZCodeEvent].
     * При обрыве соединения поток завершается (подписчик может переподписаться).
     */
    fun stream(token: String, sessionId: String): Flow<ZCodeEvent> = flow {
        val url = base.newBuilder()
            .addPathSegment("events")
            .addQueryParameter("session", sessionId)
            .build()
        val request = Request.Builder().url(url)
            .header("Authorization", "Bearer $token")
            .build()
        var response: Response? = null
        try {
            response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                emit(ZCodeEvent.Done(ok = false, error = "Мост ответил ${response.code}"))
                return@flow
            }
            val source: BufferedSource = response.body?.source() ?: run {
                emit(ZCodeEvent.Done(ok = false, error = "Пустой ответ моста"))
                return@flow
            }
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data: ")) continue
                val data = line.removePrefix("data: ").trim()
                if (data.isEmpty() || data.startsWith(":")) continue
                val event = parseEvent(data) ?: continue
                emit(event)
            }
        } catch (e: Exception) {
            emit(ZCodeEvent.Status(running = false))
            emit(ZCodeEvent.Done(ok = false, error = "Поток прерван: ${e.message ?: e.javaClass.simpleName}"))
        } finally {
            response?.close()
        }
    }.flowOn(Dispatchers.IO)

    private fun execute(request: Request): String =
        try {
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Мост ответил ${resp.code}${body.take(120).let { if (it.isNotBlank()) ": $it" else "" }}")
                }
                body
            }
        } catch (e: Exception) {
            android.util.Log.e("ZCodeApi", "request failed: ${request.url}", e)
            throw e
        }

    /** Разбор одного SSE-сообщения моста в событие сессии. */
    private fun parseEvent(data: String): ZCodeEvent? = runCatching {
        val msg = JSONObject(data)
        val method = msg.optString("method")
        if (method != "session/event" && method != "state.updated") return@runCatching null
        val params = msg.optJSONObject("params") ?: return@runCatching null
        when (params.optString("type")) {
            "model.streaming" -> {
                val payload = params.optJSONObject("payload") ?: return@runCatching null
                if (payload.optString("kind") == "text_delta") {
                    ZCodeEvent.Delta(payload.optString("delta"))
                } else null // reasoning/прочее — пропускаем
            }
            "tool.updated" -> {
                val payload = params.optJSONObject("payload") ?: return@runCatching null
                ZCodeEvent.Tool(describeTool(payload))
            }
            "state.updated" -> {
                val patch = params.optJSONObject("patch") ?: return@runCatching null
                when (patch.optString("status")) {
                    "running" -> ZCodeEvent.Status(true)
                    "idle" -> ZCodeEvent.Status(false)
                    else -> null
                }
            }
            "turn.completed" -> ZCodeEvent.Done(ok = true)
            "turn.failed" -> {
                val payload = params.optJSONObject("payload")
                ZCodeEvent.Done(ok = false, error = payload?.optString("error") ?: "Ход завершился ошибкой")
            }
            else -> null
        }
    }.getOrNull()

    /** Человекочитаемая строка о действии инструмента. */
    private fun describeTool(payload: JSONObject): String {
        val tool = payload.optString("toolName").ifBlank {
            payload.optString("name").ifBlank { "tool" }
        }
        val status = payload.optString("status").ifBlank { "выполняется" }
        val input = payload.optJSONObject("input") ?: JSONObject()
        val target = input.optString("path").ifBlank { input.optString("pattern").ifBlank { "" } }
        return if (target.isNotEmpty()) "🛠 $tool: $target — $status" else "🛠 $tool — $status"
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}