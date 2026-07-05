package com.tuapp.recordatorio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Envía la imagen de la guía de tareas a la API de Claude (modelo con visión)
 * y le pide que devuelva las tareas encontradas en formato JSON.
 *
 * IMPORTANTE: necesitas tu propia clave de API de Anthropic.
 * Consíguela en https://console.anthropic.com/
 */
object ClaudeVisionClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    data class ExtractedTask(val title: String, val description: String, val dueDateMillis: Long)

    /**
     * @param base64Image la foto codificada en base64 (sin el prefijo data:image/...)
     * @param apiKey tu clave de Anthropic
     * @param nowMillis fecha/hora actual del dispositivo, para que la IA pueda inferir años/fechas relativas
     */
    suspend fun extractTasks(base64Image: String, apiKey: String, nowMillis: Long): List<ExtractedTask> =
        withContext(Dispatchers.IO) {

            val systemPrompt = """
                Eres un asistente que lee fotos de guías, tareas escolares o de trabajo escritas a mano
                o impresas, y extrae la información en JSON puro, SIN texto adicional ni explicación.
                La fecha y hora actual es: ${'$'}nowMillis (epoch millis).
                Devuelve ÚNICAMENTE un arreglo JSON con este formato exacto:
                [
                  {"title": "string corto", "description": "detalles de la tarea", "dueDate": "YYYY-MM-DDTHH:mm:ss"}
                ]
                Si no hay una fecha explícita en la imagen, usa una fecha razonable (por ejemplo,
                una semana después de la fecha actual) e indícalo en la descripción.
                Si hay varias tareas en la imagen, devuelve varios objetos en el arreglo.
            """.trimIndent()

            val body = JSONObject().apply {
                put("model", "claude-sonnet-4-6")
                put("max_tokens", 1024)
                put("system", systemPrompt)
                put("messages", JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "image")
                                put("source", JSONObject().apply {
                                    put("type", "base64")
                                    put("media_type", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", "Extrae las tareas de esta imagen en el formato JSON indicado.")
                            })
                        })
                    }
                ))
            }

            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val responseText = response.body?.string() ?: throw Exception("Respuesta vacía de la API")
                if (!response.isSuccessful) {
                    throw Exception("Error de API (${response.code}): $responseText")
                }
                parseClaudeResponse(responseText)
            }
        }

    private fun parseClaudeResponse(responseText: String): List<ExtractedTask> {
        val json = JSONObject(responseText)
        val contentArr = json.getJSONArray("content")
        val textBlock = StringBuilder()
        for (i in 0 until contentArr.length()) {
            val block = contentArr.getJSONObject(i)
            if (block.optString("type") == "text") {
                textBlock.append(block.getString("text"))
            }
        }

        // Limpiar posibles ```json ... ``` que a veces agrega el modelo
        val cleaned = textBlock.toString()
            .replace("```json", "")
            .replace("```", "")
            .trim()

        val tasksArray = JSONArray(cleaned)
        val results = mutableListOf<ExtractedTask>()
        for (i in 0 until tasksArray.length()) {
            val obj = tasksArray.getJSONObject(i)
            val dueDateStr = obj.getString("dueDate") // formato ISO: YYYY-MM-DDTHH:mm:ss
            val millis = isoToMillis(dueDateStr)
            results.add(
                ExtractedTask(
                    title = obj.getString("title"),
                    description = obj.optString("description", ""),
                    dueDateMillis = millis
                )
            )
        }
        return results
    }

    private fun isoToMillis(iso: String): Long {
        return try {
            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            formatter.parse(iso)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}
