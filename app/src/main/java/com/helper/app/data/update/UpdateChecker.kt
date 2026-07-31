package com.helper.app.data.update

import com.helper.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Результат проверки обновления.
 */
sealed interface UpdateCheckResult {
    /** Обновление не требуется — стоит последняя версия. */
    data object UpToDate : UpdateCheckResult

    /** Доступна новая версия.
     * @param versionName строка вида "1.4.0".
     * @param apkUrl       прямая ссылка на скачивание APK.
     * @param notes        заметки релиза (markdown). */
    data class Available(
        val versionName: String,
        val apkUrl: String,
        val notes: String,
    ) : UpdateCheckResult

    /** Не удалось проверить (нет сети / GitHub недоступен). */
    data class Error(val message: String) : UpdateCheckResult
}

/**
 * Проверяет наличие свежего релиза в GitHub Releases.
 * Сравнивает versionName (семантически: major.minor.patch).
 */
class UpdateChecker {

    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(UpdateConfig.LATEST_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 15_000
                readTimeout = 20_000
            }
            val code = conn.responseCode
            val payload = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use(BufferedReader::readText) ?: ""
            if (code !in 200..299) {
                return@withContext UpdateCheckResult.Error("GitHub вернул $code")
            }

            val json = JSONObject(payload)
            val tag = json.optString("tag_name").removePrefix("v") // "v1.4.0" → "1.4.0"
            val notes = json.optString("body").ifBlank { "Без описания изменений." }

            // Ищем ассет с APK.
            val asset = json.optJSONArray("assets")?.let { arr ->
                (0 until arr.length()).map { arr.getJSONObject(it) }
                    .firstOrNull { it.optString("name") == UpdateConfig.APK_ASSET_NAME }
            }
            val apkUrl = asset?.optString("browser_download_url")
                ?: return@withContext UpdateCheckResult.Error("APK не найден в релизе $tag")

            if (isNewer(tag, BuildConfig.VERSION_NAME)) {
                UpdateCheckResult.Available(versionName = tag, apkUrl = apkUrl, notes = notes)
            } else {
                UpdateCheckResult.UpToDate
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error("Не удалось проверить: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            conn?.disconnect()
        }
    }

    /** Семантическое сравнение: true, если [remote] новее [current]. */
    private fun isNewer(remote: String, current: String): Boolean {
        val r = parseSemver(remote)
        val c = parseSemver(current)
        return when {
            r[0] != c[0] -> r[0] > c[0]
            r[1] != c[1] -> r[1] > c[1]
            else -> r[2] > c[2]
        }
    }

    private fun parseSemver(v: String): IntArray {
        val clean = v.substringBefore("-").split(".")
        return IntArray(3) { i -> clean.getOrNull(i)?.toIntOrNull() ?: 0 }
    }
}
