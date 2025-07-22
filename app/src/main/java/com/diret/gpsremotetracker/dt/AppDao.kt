package com.diret.gpsremotetracker.dt

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppDao {
    // --- Sensor Data Queries ---
    @Insert
    suspend fun insertSensorData(data: SensorData)

    @Query("SELECT * FROM sensor_data WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getSensorDataInRange(startTime: Long, endTime: Long): List<SensorData>

    // --- Auth Credentials Queries ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAuthToken(credentials: AuthCredentials)

    @Query("SELECT * FROM auth_credentials WHERE authToken = :token LIMIT 1")
    suspend fun findCredentialByToken(token: String): AuthCredentials?
}