package com.helper.app.data.local

import android.content.Context
import com.helper.app.data.model.ChatMessage
import org.json.JSONArray
import org.json.JSONObject

/**
 * Простое локальное хранилище истории чата на SharedPreferences + JSON.
 * Без Room — для MVP избыточно, а история нужна лишь чтобы не терять диалог
 * между запусками.
 */
class ChatStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<ChatMessage> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ChatMessage(
                    id = o.getString("id"),
                    role = o.getString("role"),
                    content = o.getString("content"),
                    timestamp = o.getLong("timestamp"),
                )
            }
        }.getOrElse { emptyList() }
    }

    fun save(messages: List<ChatMessage>) {
        val arr = JSONArray()
        messages.forEach { m ->
            arr.put(JSONObject()
                .put("id", m.id)
                .put("role", m.role)
                .put("content", m.content)
                .put("timestamp", m.timestamp))
        }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private companion object {
        const val PREFS_NAME = "helper_chat"
        const val KEY_HISTORY = "history"
    }
}
