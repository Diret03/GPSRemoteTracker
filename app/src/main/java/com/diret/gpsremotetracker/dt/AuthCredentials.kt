package com.diret.gpsremotetracker.dt

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "auth_credentials")
data class AuthCredentials(
    @PrimaryKey val id: Int = 1, // Solo una credencial
    val authToken: String
)