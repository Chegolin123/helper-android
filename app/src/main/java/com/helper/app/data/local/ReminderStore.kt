package com.helper.app.data.local

import android.content.Context
import com.helper.app.data.model.Reminder
import org.json.JSONArray
import org.json.JSONObject

/**
 * Локальное хранилище напоминаний (Фича 1) на SharedPreferences + JSON.
 * Без Room — как и история чата, чтобы не тащить зависимость.
 */
class ReminderStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<Reminder> {
        val raw = prefs.getString(KEY_REMINDERS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Reminder(
                    id = o.getInt("id"),
                    text = o.getString("text"),
                    dueEpoch = o.getLong("due"),
                )
            }
        }.getOrElse { emptyList() }
    }

    fun save(reminders: List<Reminder>) {
        val arr = JSONArray()
        reminders.forEach { r ->
            arr.put(JSONObject()
                .put("id", r.id)
                .put("text", r.text)
                .put("due", r.dueEpoch))
        }
        prefs.edit().putString(KEY_REMINDERS, arr.toString()).apply()
    }

    fun add(reminder: Reminder) {
        save(load() + reminder)
    }

    fun remove(id: Int) {
        save(load().filterNot { it.id == id })
    }

    /** Следующий свободный id. */
    fun nextId(): Int = (load().maxOfOrNull { it.id } ?: 0) + 1

    private companion object {
        const val PREFS_NAME = "helper_reminders"
        const val KEY_REMINDERS = "reminders"
    }
}
