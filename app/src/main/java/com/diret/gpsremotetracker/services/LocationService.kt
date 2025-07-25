package com.diret.gpsremotetracker.services

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.diret.gpsremotetracker.dt.AppDatabase
import com.diret.gpsremotetracker.dt.SensorData
import com.diret.gpsremotetracker.state.DataCollectionStateHolder
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class LocationService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var db: AppDatabase
    private lateinit var settingsManager: SettingsManager

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getDatabase(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        settingsManager = SettingsManager(this)
        setupLocationCallback()
        Log.d("LocationService", "Service Created.")
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("LocationService", "Service Started.")
        startForegroundService()

        val intervalSeconds = settingsManager.getCollectionInterval().toLong()
        val intervalMillis = TimeUnit.SECONDS.toMillis(intervalSeconds)
        Log.d("LocationService", "Configurando la recolección cada $intervalSeconds segundos.")

        // --- LÓGICA CORREGIDA ---
        // Ahora todos los parámetros de tiempo se basan en el mismo valor,
        // lo que le da al sistema una instrucción clara y consistente.
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(intervalMillis) // El intervalo mínimo es el mismo que el principal.
            .setMaxUpdateDelayMillis(intervalMillis + TimeUnit.SECONDS.toMillis(5)) // Un pequeño margen de retraso.
            .build()
        // --- FIN DE LA CORRECCIÓN ---

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())

        return START_STICKY
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (!settingsManager.isCollectionTimeActive()) {
                    Log.v("LocationService", "Skipping data collection outside of scheduled time.")
                    return
                }

                locationResult.lastLocation?.let { location ->
                    Log.d("LocationService", "New location received: Lat=${location.latitude}, Lon=${location.longitude}")
                    val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                    val sensorData = SensorData(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        timestamp = System.currentTimeMillis(),
                        deviceId = deviceId
                    )
                    scope.launch {
                        db.appDao().insertSensorData(sensorData)
                        Log.i("LocationService", "Sensor data inserted into database.")
                        DataCollectionStateHolder.notifyDataSaved(sensorData)
                    }
                }
            }
        }
    }

    private fun startForegroundService() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val channelId = "location_service_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Location Service", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Monitor Remoto Activo")
            .setContentText("Recolectando datos de ubicación en segundo plano.")
            .setSmallIcon(com.diret.gpsremotetracker.R.mipmap.ic_launcher)
            .build()

        startForeground(1, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        job.cancel()
    }
}
