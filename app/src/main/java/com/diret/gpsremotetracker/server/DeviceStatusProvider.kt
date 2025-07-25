package com.diret.gpsremotetracker.server

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs

/**
 * Clase auxiliar para obtener información del estado actual del dispositivo.
 * Requiere el contexto de la aplicación para acceder a los servicios del sistema.
 * @param context El contexto de la aplicación.
 */
class DeviceStatusProvider(private val context: Context) {

    /**
     * Recopila y devuelve un mapa con el estado completo del dispositivo.
     */
    fun getDeviceStatus(): Map<String, Any> {
        return mapOf(
            "battery" to getBatteryStatus(),
            "network" to getNetworkStatus(),
            "storage" to getStorageStatus(),
            "osVersion" to Build.VERSION.RELEASE,
            "deviceModel" to Build.MODEL,
            "sdkVersion" to Build.VERSION.SDK_INT
        )
    }

    /**
     * Obtiene el estado de la batería, incluyendo el nivel y si está cargando.
     */
    private fun getBatteryStatus(): Map<String, Any> {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        // Usar registerReceiver con null es seguro aquí para obtener un intent "pegajoso" (sticky).
        val batteryStatus: Intent? = context.registerReceiver(null, intentFilter)

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level != -1 && scale != -1) {
            (level / scale.toFloat()) * 100
        } else {
            -1f // Valor por defecto en caso de error
        }

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        return mapOf(
            "levelPercent" to batteryPct.toInt(),
            "isCharging" to isCharging
        )
    }

    /**
     * Obtiene el estado de la conectividad de red.
     */
    private fun getNetworkStatus(): Map<String, Any> {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

        val isConnected = capabilities != null
        val connectionType = when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            else -> "Not Connected"
        }

        return mapOf(
            "isConnected" to isConnected,
            "connectionType" to connectionType
        )
    }

    /**
     * Obtiene el estado del almacenamiento interno del dispositivo.
     */
    private fun getStorageStatus(): Map<String, Any> {
        val stat = StatFs(Environment.getDataDirectory().path)
        val bytesAvailable = stat.availableBytes
        val totalBytes = stat.totalBytes
        val gbAvailable = bytesAvailable / (1024.0 * 1024.0 * 1024.0)
        val gbTotal = totalBytes / (1024.0 * 1024.0 * 1024.0)

        return mapOf(
            "availableGB" to String.format("%.2f", gbAvailable),
            "totalGB" to String.format("%.2f", gbTotal)
        )
    }
}
