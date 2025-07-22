package com.diret.gpsremotetracker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.diret.gpsremotetracker.dt.AppDatabase
import com.diret.gpsremotetracker.dt.AuthCredentials
import com.diret.gpsremotetracker.server.WebServer
import com.diret.gpsremotetracker.services.LocationService
import com.diret.gpsremotetracker.ui.theme.GPSRemoteTrackerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class MainActivity : ComponentActivity() {

    private var webServer: WebServer? = null
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = AppDatabase.getDatabase(this)
        webServer = WebServer(db.appDao())

        setContent {
            GPSRemoteTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // pantalla principal Composable
                    MainScreen(
                        db = db,
                        onStartService = {
                            startLocationService()
                            webServer?.start()
                        },
                        onStopService = {
                            stopLocationService()
                            webServer?.stop()
                        }
                    )
                }
            }
        }
    }

    private fun startLocationService() {
        val serviceIntent = Intent(this, LocationService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun stopLocationService() {
        stopService(Intent(this, LocationService::class.java))
    }

    override fun onDestroy() {
        super.onDestroy()
        webServer?.stop()
    }
}

// --- Interfaz de Usuario con Jetpack Compose ---

@Composable
fun MainScreen(
    db: AppDatabase,
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    val context = LocalContext.current

    // "remember" guarda el estado a través de las recomposiciones
    var hasPermissions by remember { mutableStateOf(false) }
    var isServiceRunning by remember { mutableStateOf(false) }
    var authToken by remember { mutableStateOf("Generando...") }


    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }
    }

    // "LaunchedEffect" ejecuta codigo una sola vez cuando el Composable entra en pantalla
    LaunchedEffect(Unit) {
        // 1. Comprobar permisos
        val requiredPermissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val allGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            hasPermissions = true
        } else {
            permissionsLauncher.launch(requiredPermissions.toTypedArray())
        }

        // 2. Obtener el token de la base de datos en un hilo secundario
        val token = withContext(Dispatchers.IO) {
            val credentials = db.appDao().findCredentialByToken("%") // Obtiene el primero que encuentre
            if (credentials != null) {
                credentials.authToken
            } else {
                val newToken = UUID.randomUUID().toString()
                db.appDao().saveAuthToken(AuthCredentials(authToken = newToken))
                newToken
            }
        }
        Log.d("MainActivity", "API Token: $token")
        authToken = token
    }

    // columna para organizar los elementos verticalmente
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Monitor Remoto de Dispositivos", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (!hasPermissions) "Permisos denegados." else if (isServiceRunning) "Estado: Recolectando..." else "Estado: Detenido",
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (isServiceRunning) {
                    Log.d("MainActivity", "Stop button clicked.")
                    onStopService()
                } else {
                    Log.d("MainActivity", "Start button clicked.")
                    onStartService()
                }
                isServiceRunning = !isServiceRunning
            },
            enabled = hasPermissions
        ) {
            Text(if (isServiceRunning) "Detener Recolección" else "Iniciar Recolección")
        }
        Spacer(modifier = Modifier.height(32.dp))

        // contenedor para que el texto se pueda seleccionar y copiar
        SelectionContainer {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isServiceRunning) "Dirección IP: ${getIpAddress(context)}:9999" else "Dirección IP: N/A",
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Token de API: $authToken",
                    fontSize = 16.sp,
                )
            }
        }
    }
}

private fun getIpAddress(context: Context): String {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val ipAddress = wifiManager.connectionInfo.ipAddress
    return if (ipAddress == 0) "No conectado a WiFi" else
        String.format(
            "%d.%d.%d.%d",
            ipAddress and 0xff,
            ipAddress shr 8 and 0xff,
            ipAddress shr 16 and 0xff,
            ipAddress shr 24 and 0xff
        )
}