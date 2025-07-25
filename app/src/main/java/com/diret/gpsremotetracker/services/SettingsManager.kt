package com.diret.gpsremotetracker.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Gestiona el almacenamiento y la recuperación de las configuraciones de la aplicación
 * utilizando SharedPreferences.
 * @param context El contexto de la aplicación.
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    companion object {
        const val KEY_SELECTED_DAYS = "selected_days"
        const val KEY_START_HOUR = "start_hour"
        const val KEY_START_MINUTE = "start_minute"
        const val KEY_END_HOUR = "end_hour"
        const val KEY_END_MINUTE = "end_minute"
        const val KEY_COLLECTION_INTERVAL = "collection_interval" // --- AÑADIDO ---
        private const val TAG = "SettingsManager"
    }

    // --- Intervalo de Recolección ---
    fun saveCollectionInterval(seconds: Int) {
        prefs.edit().putInt(KEY_COLLECTION_INTERVAL, seconds).apply()
    }

    fun getCollectionInterval(): Int {
        // El valor por defecto es 30 segundos, como pide el desafío.
        return prefs.getInt(KEY_COLLECTION_INTERVAL, 30)
    }

    // --- Días de la Semana ---
    fun saveSelectedDays(days: Set<Int>) {
        val stringSet = days.map { it.toString() }.toSet()
        prefs.edit().putStringSet(KEY_SELECTED_DAYS, stringSet).apply()
    }

    fun getSelectedDays(): Set<Int> {
        val defaultDays = (0..6).map { it.toString() }.toSet()
        return prefs.getStringSet(KEY_SELECTED_DAYS, defaultDays)?.mapNotNull { it.toIntOrNull() }?.toSet() ?: defaultDays.map { it.toInt() }.toSet()
    }

    // --- Hora de Inicio ---
    fun saveStartTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_START_HOUR, hour)
            .putInt(KEY_START_MINUTE, minute)
            .apply()
    }

    fun getStartTime(): Pair<Int, Int> {
        val hour = prefs.getInt(KEY_START_HOUR, 22) // 22:00 por defecto
        val minute = prefs.getInt(KEY_START_MINUTE, 0)
        return Pair(hour, minute)
    }

    // --- Hora de Fin ---
    fun saveEndTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_END_HOUR, hour)
            .putInt(KEY_END_MINUTE, minute)
            .apply()
    }

    fun getEndTime(): Pair<Int, Int> {
        val hour = prefs.getInt(KEY_END_HOUR, 0) // 00:59 por defecto
        val minute = prefs.getInt(KEY_END_MINUTE, 59)
        return Pair(hour, minute)
    }

    /**
     * Comprueba si la recolección de datos debe estar activa en el momento actual
     * según la configuración guardada.
     */
    fun isCollectionTimeActive(): Boolean {
        val calendar = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        Log.d(TAG, "--- Verificando Horario de Recolección ---")
        Log.d(TAG, "Hora Actual del Dispositivo: ${sdf.format(calendar.time)}")

        // 1. Verificar el día
        val activeDaysIndices = getSelectedDays()
        val activeCalendarDays = activeDaysIndices.map { mapDayToCalendar(it) }.toSet()
        val currentDay = calendar.get(Calendar.DAY_OF_WEEK)
        val isDayActive = activeCalendarDays.contains(currentDay)
        Log.d(TAG, "Día actual: ${calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault())} ($currentDay). ¿Está activo? $isDayActive")

        if (!isDayActive) {
            Log.d(TAG, "Resultado: INACTIVO (Día fuera de horario).")
            Log.d(TAG, "--------------------------------------")
            return false
        }

        // 2. Verificar la hora
        val (startHour, startMinute) = getStartTime()
        val (endHour, endMinute) = getEndTime()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        val startTimeInMinutes = startHour * 60 + startMinute
        val endTimeInMinutes = endHour * 60 + endMinute
        val currentTimeInMinutes = currentHour * 60 + currentMinute

        // --- LÓGICA CORREGIDA PARA HORARIOS NOCTURNOS ---
        val isTimeActive = if (startTimeInMinutes <= endTimeInMinutes) {
            // Horario normal (ej. 09:00 a 18:00)
            currentTimeInMinutes in startTimeInMinutes..endTimeInMinutes
        } else {
            // Horario nocturno que cruza la medianoche (ej. 22:00 a 02:00)
            currentTimeInMinutes >= startTimeInMinutes || currentTimeInMinutes <= endTimeInMinutes
        }
        // --- FIN DE LA CORRECCIÓN ---

        Log.d(TAG, "Horario configurado: de $startHour:$startMinute a $endHour:$endMinute")
        Log.d(TAG, "Hora actual: $currentHour:$currentMinute. ¿Está activa? $isTimeActive")

        val finalResult = isDayActive && isTimeActive
        Log.d(TAG, "Resultado Final: ${if (finalResult) "ACTIVO" else "INACTIVO"}")
        Log.d(TAG, "--------------------------------------")
        return finalResult
    }

    private fun mapDayToCalendar(dayIndex: Int): Int {
        return when (dayIndex) {
            0 -> Calendar.MONDAY
            1 -> Calendar.TUESDAY
            2 -> Calendar.WEDNESDAY
            3 -> Calendar.THURSDAY
            4 -> Calendar.FRIDAY
            5 -> Calendar.SATURDAY
            6 -> Calendar.SUNDAY
            else -> -1
        }
    }
}
