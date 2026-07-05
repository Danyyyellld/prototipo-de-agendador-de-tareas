package com.tuapp.recordatorio

import org.json.JSONObject

/**
 * Representa una tarea pendiente extraída de una foto o creada manualmente.
 */
data class Task(
    val id: String,
    val title: String,
    val description: String,
    val dueDateMillis: Long,   // fecha límite en milisegundos (epoch)
    val calendarEventId: Long? = null,
    val completed: Boolean = false
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("title", title)
        obj.put("description", description)
        obj.put("dueDateMillis", dueDateMillis)
        obj.put("calendarEventId", calendarEventId ?: -1L)
        obj.put("completed", completed)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): Task {
            val eventId = obj.getLong("calendarEventId")
            return Task(
                id = obj.getString("id"),
                title = obj.getString("title"),
                description = obj.optString("description", ""),
                dueDateMillis = obj.getLong("dueDateMillis"),
                calendarEventId = if (eventId == -1L) null else eventId,
                completed = obj.optBoolean("completed", false)
            )
        }
    }
}
