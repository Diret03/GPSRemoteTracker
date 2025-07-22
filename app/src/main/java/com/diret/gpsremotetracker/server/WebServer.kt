package com.diret.gpsremotetracker.server

import android.util.Log
import com.diret.gpsremotetracker.dt.AppDao
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.serialization.gson.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class WebServer(private val dao: AppDao) {

    private var server: CIOApplicationEngine? = null

    fun start() {
        if (server?.application?.isActive == true) {
            Log.d("WebServer", "Server is already running.")
            return
        }
        Log.d("WebServer", "Attempting to start Ktor server...")

        server = embeddedServer(CIO, port = 9999, host = "0.0.0.0") {
            install(ContentNegotiation) {
                gson()
            }

            routing {
                route("/api") {
                    install(Authentication)

                    get("/sensor_data") {
                        Log.i("WebServer", "Request received for /api/sensor_data")
                        val startTime = call.request.queryParameters["start_time"]?.toLongOrNull()
                        val endTime = call.request.queryParameters["end_time"]?.toLongOrNull()

                        if (startTime == null || endTime == null) {
                            call.respond(HttpStatusCode.BadRequest, "Faltan los parámetros 'start_time' y 'end_time'")
                            return@get
                        }

                        val data = dao.getSensorDataInRange(startTime, endTime)
                        Log.d("WebServer", "Found ${data.size} records in database for the given time range.")
                        call.respond(data)
                    }

                    get("/device_status") {
                        Log.i("WebServer", "Request received for /api/device_status")
                        val statusInfo = mapOf(
                            "batteryLevel" to 95,
                            "networkStatus" to "WiFi",
                            "osVersion" to "Android 14"
                        )
                        call.respond(statusInfo)
                    }
                }
            }
        }
        server?.start(wait = false)
        Log.i("WebServer", "Ktor server started successfully on port 9999.")
    }

    fun stop() {
        Log.w("WebServer", "Stopping Ktor server.")
        server?.stop(1000, 2000)
        server = null
    }

    private val Authentication = createRouteScopedPlugin("Authentication") {
        onCall { call ->
            Log.d("WebServerAuth", "Intercepted request for: ${call.request.uri}")
            val authHeader = call.request.header("Authorization")

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                Log.w("WebServerAuth", "Authentication failed: Missing or invalid 'Bearer' token format.")
                call.respond(HttpStatusCode.Unauthorized, "Cabecera de autenticación no encontrada o inválida.")
                return@onCall
            }

            val token = authHeader.removePrefix("Bearer ").trim()
            Log.d("WebServerAuth", "Validating token: $token")
            val credential = withContext(Dispatchers.IO) {
                dao.findCredentialByToken(token)
            }

            if (credential == null) {
                Log.w("WebServerAuth", "Authentication failed: Token not found in database.")
                call.respond(HttpStatusCode.Unauthorized, "Token inválido.")
                return@onCall
            }

            Log.i("WebServerAuth", "Authentication successful for token: $token")
        }
    }
}