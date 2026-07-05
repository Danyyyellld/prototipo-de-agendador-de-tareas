package com.tuapp.recordatorio

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import java.util.TimeZone

/**
 * Funciones para crear eventos con recordatorio directamente
 * en el Calendario del teléfono (Google Calendar u otro que uses).
 */
object CalendarHelper {

    /** Devuelve el ID del primer calendario disponible en el dispositivo. */
    fun getPrimaryCalendarId(context: Context): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY)
        val cursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null, null, null
        ) ?: return null

        var fallbackId: Long? = null
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                if (fallbackId == null) fallbackId = id
                val isPrimaryIdx = it.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
                if (isPrimaryIdx >= 0 && it.getInt(isPrimaryIdx) == 1) {
                    return id
                }
            }
        }
        return fallbackId
    }

    /**
     * Crea un evento de todo el día o con hora específica y un recordatorio.
     * @param startMillis fecha/hora de la tarea (fecha límite)
     * @param reminderMinutesBefore minutos antes para el recordatorio (por defecto 60 = 1 hora)
     * @return el ID del evento creado, o null si falló
     */
    fun insertEvent(
        context: Context,
        title: String,
        description: String,
        startMillis: Long,
        reminderMinutesBefore: Int = 60
    ): Long? {
        val calId = getPrimaryCalendarId(context) ?: return null

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, startMillis + 30 * 60 * 1000) // 30 min de duración
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }

        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: return null
        val eventId = ContentUris.parseId(uri)

        // Agregar recordatorio (aparecerá como notificación del sistema)
        val reminderValues = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, reminderMinutesBefore)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)

        return eventId
    }

    fun deleteEvent(context: Context, eventId: Long) {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        context.contentResolver.delete(uri, null, null)
    }
}
