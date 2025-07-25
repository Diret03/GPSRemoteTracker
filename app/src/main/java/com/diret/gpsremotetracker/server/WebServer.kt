package com.diret.gpsremotetracker.server

import android.content.Context
import android.util.Log
import com.diret.gpsremotetracker.dt.AppDao
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementación del servidor web integrado usando Ktor.
 * @param context El contexto de la aplicación, necesario para el DeviceStatusProvider.
 * @param dao El objeto de acceso a datos (DAO) para consultar la base de datos.
 */
class WebServer(private val context: Context, private val dao: AppDao) {

    private var server: CIOApplicationEngine? = null
    private val deviceStatusProvider = DeviceStatusProvider(context)

    fun start() {
        if (server != null) {
            Log.d("WebServer", "El servidor ya está en ejecución.")
            return
        }
        Log.d("WebServer", "Intentando iniciar el servidor Ktor...")

        server = embeddedServer(CIO, port = 9999, host = "0.0.0.0") {
            install(ContentNegotiation) {
                gson {
                    setPrettyPrinting()
                }
            }

            routing {
                route("/api") {
                    install(Authentication)

                    get("/sensor_data") {
                        Log.i("WebServer", "Petición recibida para /api/sensor_data")
                        val startTime = call.request.queryParameters["start_time"]?.toLongOrNull()
                        val endTime = call.request.queryParameters["end_time"]?.toLongOrNull()

                        if (startTime == null || endTime == null) {
                            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Faltan los parámetros 'start_time' y 'end_time'"))
                            return@get
                        }

                        val data = withContext(Dispatchers.IO) {
                            dao.getSensorDataInRange(startTime, endTime)
                        }
                        Log.d("WebServer", "Se encontraron ${data.size} registros para el rango de tiempo especificado.")
                        call.respond(data)
                    }

                    get("/device_status") {
                        Log.i("WebServer", "Petición recibida para /api/device_status")
                        val statusInfo = deviceStatusProvider.getDeviceStatus()
                        call.respond(statusInfo)
                    }
                }
            }
        }
        server?.start(wait = false)
        Log.i("WebServer", "Servidor Ktor iniciado correctamente en el puerto 9999.")
    }

    fun stop() {
        if (server == null) {
            Log.d("WebServer", "El servidor no estaba en ejecución.")
            return
        }
        Log.w("WebServer", "Deteniendo el servidor Ktor.")
        server?.stop(1000, 2000)
        server = null
        Log.i("WebServer", "Servidor Ktor detenido.")
    }

    // Plugin de autenticación personalizado para Ktor.
    private val Authentication = createRouteScopedPlugin("Authentication") {
        onCall { call ->
            Log.d("WebServerAuth", "Interceptando petición para: ${call.request.uri}")
            val authHeader = call.request.header("Authorization")

            if (authHeader == null || !authHeader.startsWith("Bearer ", ignoreCase = true)) {
                Log.w("WebServerAuth", "Autenticación fallida: Cabecera 'Bearer' no encontrada o con formato inválido.")
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Cabecera de autenticación no encontrada o inválida."))
                return@onCall
            }

            val token = authHeader.removePrefix("Bearer ").trim()
            Log.d("WebServerAuth", "Validando token: '$token'")

            val credential = withContext(Dispatchers.IO) {
                dao.findCredentialByToken(token)
            }

            if (credential == null) {
                Log.w("WebServerAuth", "Autenticación fallida: Token no encontrado en la base de datos.")
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token inválido."))
                return@onCall
            }

            // CORRECCIÓN: Se usa la propiedad 'authToken' que sí existe en tu clase AuthCredentials.
            Log.i("WebServerAuth", "Autenticación exitosa para el token: ${credential.authToken}")
        }
    }
}
