package com.tuapp.recordatorio

import android.content.Context
import org.json.JSONArray
import java.util.UUID

/**
 * Guarda y lee las tareas usando SharedPreferences en formato JSON.
 * Es una solución simple, ideal para una app personal sin necesidad de base de datos.
 */
class TaskRepository(context: Context) {

    private val prefs = context.getSharedPreferences("recordatorio_prefs", Context.MODE_PRIVATE)

    fun getAll(): List<Task> {
        val raw = prefs.getString(KEY_TASKS, null) ?: return emptyList()
        val arr = JSONArray(raw)
        val list = mutableListOf<Task>()
        for (i in 0 until arr.length()) {
            list.add(Task.fromJson(arr.getJSONObject(i)))
        }
        return list.sortedBy { it.dueDateMillis }
    }

    fun add(task: Task) {
        val current = getAll().toMutableList()
        current.add(task)
        saveAll(current)
    }

    fun update(task: Task) {
        val current = getAll().toMutableList()
        val idx = current.indexOfFirst { it.id == task.id }
        if (idx >= 0) {
            current[idx] = task
            saveAll(current)
        }
    }

    fun delete(taskId: String) {
        val current = getAll().filterNot { it.id == taskId }
        saveAll(current)
    }

    private fun saveAll(tasks: List<Task>) {
        val arr = JSONArray()
        tasks.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_TASKS, arr.toString()).apply()
    }

    // --- Guardar la clave de API de Claude que ingresa el usuario ---
    fun saveApiKey(key: String) {
        prefs.edit().putString(KEY_API, key).apply()
    }

    fun getApiKey(): String? = prefs.getString(KEY_API, null)

    companion object {
        private const val KEY_TASKS = "tasks_json"
        private const val KEY_API = "claude_api_key"

        fun newId(): String = UUID.randomUUID().toString()
    }
}
