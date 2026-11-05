package com.helper.app.data.dsh

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.helper.app.data.model.ChatMessage
import org.json.JSONArray
import org.json.JSONObject

/**
 * Зашифрованное хранилище нативного DSH-чата: токен remote-mobile,
 * id серверной сессии и локальная копия истории.
 */
class DshStore(context: Context) {

    private val prefs: SharedPreferences = runCatching {
        EncryptedSharedPreferences.create(
            "dsh_secure",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        // Keystore на редких прошивках бывает недоступен; чат важнее шифрования.
        context.applicationContext.getSharedPreferences("dsh_secure_fallback", Context.MODE_PRIVATE)
    }

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) = prefs.edit().putString(KEY_BASE_URL, value.trimEnd('/')).apply()

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) remove(KEY_TOKEN) else putString(KEY_TOKEN, value)
            }.apply()
        }

    var sessionId: String?
        get() = prefs.getString(KEY_SESSION_ID, null)
        set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) remove(KEY_SESSION_ID) else putString(KEY_SESSION_ID, value)
            }.apply()
        }

    fun loadMessages(): List<ChatMessage> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                ChatMessage(
                    id = o.optString("id"),
                    role = o.optString("role"),
                    content = o.optString("content"),
                    timestamp = o.optLong("timestamp"),
                )
            }
        }.getOrElse { emptyList() }
    }

    fun saveMessages(messages: List<ChatMessage>) {
        val arr = JSONArray()
        messages.takeLast(MAX_HISTORY).forEach { m ->
            arr.put(
                JSONObject()
                    .put("id", m.id)
                    .put("role", m.role)
                    .put("content", m.content)
                    .put("timestamp", m.timestamp)
            )
        }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://assistant.nexgate.ru"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_TOKEN = "token"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_HISTORY = "history"
        private const val MAX_HISTORY = 200
    }
}
