package com.diret.gpsremotetracker

import android.Manifest
import android.app.TimePickerDialog
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.diret.gpsremotetracker.dt.AppDatabase
import com.diret.gpsremotetracker.dt.AuthCredentials
import com.diret.gpsremotetracker.dt.SensorData
import com.diret.gpsremotetracker.server.DeviceStatusProvider
import com.diret.gpsremotetracker.server.WebServer
import com.diret.gpsremotetracker.services.LocationService
import com.diret.gpsremotetracker.services.SettingsManager
import com.diret.gpsremotetracker.state.DataCollectionStateHolder
import com.diret.gpsremotetracker.ui.theme.GPSRemoteTrackerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private var webServer: WebServer? = null
    private lateinit var db: AppDatabase
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = AppDatabase.getDatabase(this)
        webServer = WebServer(this, db.appDao())
        settingsManager = SettingsManager(this)

        setContent {
            GPSRemoteTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        db = db,
                        settingsManager = settingsManager,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    db: AppDatabase,
    settingsManager: SettingsManager,
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var hasPermissions by remember { mutableStateOf(false) }
    var isServiceRunning by remember { mutableStateOf(false) }
    var authToken by remember { mutableStateOf<String?>(null) }
    var lastSensorData by remember { mutableStateOf<SensorData?>(null) }
    var deviceStatus by remember { mutableStateOf<Map<String, Any>?>(null) }
    val deviceStatusProvider = remember { DeviceStatusProvider(context) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }
    }

    LaunchedEffect(Unit) {
        val requiredPermissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        hasPermissions = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!hasPermissions) {
            permissionsLauncher.launch(requiredPermissions.toTypedArray())
        }

        launch(Dispatchers.IO) {
            val token = db.appDao().findCredentialByToken("%")?.authToken ?: run {
                val newToken = UUID.randomUUID().toString()
                db.appDao().saveAuthToken(AuthCredentials(authToken = newToken))
                newToken
            }
            authToken = token
        }

        launch {
            DataCollectionStateHolder.lastDataSaved.collect { data ->
                lastSensorData = data
            }
        }
    }

    LaunchedEffect(isServiceRunning) {
        if (isServiceRunning) {
            while (true) {
                deviceStatus = withContext(Dispatchers.IO) {
                    deviceStatusProvider.getDeviceStatus()
                }
                delay(10000)
            }
        } else {
            deviceStatus = null
        }
    }

    // --- MODIFICADO: Se usa un Box para poder posicionar el Snackbar arriba ---
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("GPS Remote Tracker") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    StatusCard(hasPermissions, isServiceRunning, lastSensorData)
                    Spacer(modifier = Modifier.height(16.dp))

                    AnimatedVisibility(visible = isServiceRunning) {
                        Column {
                            DeviceStatusCard(status = deviceStatus)
                            Spacer(modifier = Modifier.height(16.dp))
                            ApiEndpointsCard(ipAddress = getIpAddress(context))
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    CredentialsCard(authToken = authToken)
                    Spacer(modifier = Modifier.height(16.dp))
                    ConfigurationCard(settingsManager)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Button(
                    onClick = {
                        val nextState = !isServiceRunning
                        if (nextState) {
                            onStartService()
                        } else {
                            onStopService()
                        }
                        isServiceRunning = nextState

                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                message = if (nextState) "Recolección de datos iniciada" else "Recolección de datos detenida"
                            )
                        }
                    },
                    enabled = hasPermissions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    AnimatedContent(targetState = isServiceRunning, label = "Button Animation") { running ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (running) "Detener Recolección" else "Iniciar Recolección",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // --- MODIFICADO: SnackbarHost posicionado en la parte superior del Box ---
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp, start = 16.dp, end = 16.dp)
        )
    }
}

@Composable
fun StatusCard(hasPermissions: Boolean, isServiceRunning: Boolean, lastSensorData: SensorData?) {
    val statusColor = when {
        !hasPermissions -> MaterialTheme.colorScheme.error
        isServiceRunning -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    }
    val statusIcon = when {
        !hasPermissions -> Icons.Default.Warning
        isServiceRunning -> Icons.Default.CheckCircle
        else -> Icons.Default.HighlightOff
    }
    val statusText = when {
        !hasPermissions -> "Permisos denegados"
        isServiceRunning -> "Servicio activo"
        else -> "Servicio detenido"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = "Status Icon",
                tint = statusColor,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = "Estado del GPS", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
                if (isServiceRunning) {
                    val feedbackText = if (lastSensorData != null) {
                        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                        "Último guardado: ${sdf.format(Date(lastSensorData.timestamp))}"
                    } else {
                        "Esperando primera ubicación..."
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = feedbackText, style = MaterialTheme.typography.bodySmall)

                    if (lastSensorData != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Lat: ${String.format("%.4f", lastSensorData.latitude)}, Lon: ${String.format("%.4f", lastSensorData.longitude)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "ID del Dispositivo: ${lastSensorData.deviceId}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceStatusCard(status: Map<String, Any>?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Estado del Dispositivo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            if (status == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cargando estado...", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                val battery = status["battery"] as? Map<*, *>
                val network = status["network"] as? Map<*, *>
                val storage = status["storage"] as? Map<*, *>

                val batteryIcon = if (battery?.get("isCharging") == true) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd
                val batteryText = "${battery?.get("levelPercent")}%" + if (battery?.get("isCharging") == true) " (Cargando)" else ""
                InfoRow(icon = batteryIcon, label = "Batería", value = batteryText)
                Divider(modifier = Modifier.padding(vertical = 8.dp))

                val networkIcon = if (network?.get("isConnected") == true) Icons.Default.SignalWifi4Bar else Icons.Default.SignalWifiOff
                val networkText = network?.get("connectionType") as? String ?: "Desconocido"
                InfoRow(icon = networkIcon, label = "Red", value = networkText)
                Divider(modifier = Modifier.padding(vertical = 8.dp))

                val storageText = "Disponible: ${storage?.get("availableGB")} GB / Total: ${storage?.get("totalGB")} GB"
                InfoRow(icon = Icons.Default.Storage, label = "Almacenamiento", value = storageText)
            }
        }
    }
}

@Composable
fun ApiEndpointsCard(ipAddress: String) {
    val clipboardManager = LocalClipboardManager.current
    val baseUrl = "http://$ipAddress:9999"

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Endpoints de la API", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "URL Base: $baseUrl",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            val deviceStatusUrl = "$baseUrl/api/device_status"
            EndpointRow(
                method = "GET",
                path = "/api/device_status",
                onCopy = { clipboardManager.setText(AnnotatedString(deviceStatusUrl)) }
            )
            Divider(modifier = Modifier.padding(vertical = 8.dp))

            val sensorDataUrl = "$baseUrl/api/sensor_data?start_time=...&end_time=..."
            EndpointRow(
                method = "GET",
                path = "/api/sensor_data",
                onCopy = { clipboardManager.setText(AnnotatedString(sensorDataUrl)) }
            )
        }
    }
}

@Composable
fun EndpointRow(method: String, path: String, onCopy: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = method,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = path, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onCopy) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copiar URL")
        }
    }
}


@Composable
fun CredentialsCard(authToken: String?) {
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            InfoRow(
                icon = Icons.Default.VpnKey,
                label = "Token de Autenticación (API)",
                value = authToken ?: "Generando...",
                onCopy = {
                    authToken?.let {
                        clipboardManager.setText(AnnotatedString(it))
                    }
                }
            )
        }
    }
}

@Composable
fun ConfigurationCard(settingsManager: SettingsManager) {
    val daysOfWeek = listOf("L", "M", "X", "J", "V", "S", "D")
    var selectedDays by remember { mutableStateOf(settingsManager.getSelectedDays()) }
    val (startHour, startMinute) = settingsManager.getStartTime()
    val (endHour, endMinute) = settingsManager.getEndTime()
    var startTime by remember { mutableStateOf(String.format("%02d:%02d", startHour, startMinute)) }
    var endTime by remember { mutableStateOf(String.format("%02d:%02d", endHour, endMinute)) }
    var sliderPosition by remember { mutableStateOf(settingsManager.getCollectionInterval().toFloat()) }
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Configuración de Recolección", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            Text("Intervalo de recolección: ${sliderPosition.roundToInt()} segundos", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it },
                valueRange = 10f..120f,
                steps = 10,
                onValueChangeFinished = {
                    settingsManager.saveCollectionInterval(sliderPosition.roundToInt())
                }
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text("Días activos:", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                daysOfWeek.forEachIndexed { index, day ->
                    val isSelected = selectedDays.contains(index)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                shape = CircleShape
                            )
                            .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .clickable {
                                val newSelection = if (isSelected) {
                                    selectedDays - index
                                } else {
                                    selectedDays + index
                                }
                                selectedDays = newSelection
                                settingsManager.saveSelectedDays(newSelection)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = day,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Text("Horario activo:", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TimeSelector(label = "Desde", time = startTime) {
                    TimePickerDialog(
                        context,
                        { _, hourOfDay, minute ->
                            settingsManager.saveStartTime(hourOfDay, minute)
                            startTime = String.format("%02d:%02d", hourOfDay, minute)
                        },
                        startHour, startMinute, true
                    ).show()
                }
                Text("-", fontWeight = FontWeight.Bold)
                TimeSelector(label = "Hasta", time = endTime) {
                    TimePickerDialog(
                        context,
                        { _, hourOfDay, minute ->
                            settingsManager.saveEndTime(hourOfDay, minute)
                            endTime = String.format("%02d:%02d", hourOfDay, minute)
                        },
                        endHour, endMinute, true
                    ).show()
                }
            }
        }
    }
}

@Composable
fun TimeSelector(label: String, time: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(
            text = time,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(8.dp)
        )
    }
}

@Composable
fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, onCopy: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
        if (onCopy != null) {
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copiar")
            }
        }
    }
}

private fun getIpAddress(context: Context): String {
    return try {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipAddress = wifiManager.connectionInfo.ipAddress
        if (ipAddress == 0) "No conectado a WiFi" else
            String.format(
                "%d.%d.%d.%d",
                ipAddress and 0xff,
                ipAddress shr 8 and 0xff,
                ipAddress shr 16 and 0xff,
                ipAddress shr 24 and 0xff
            )
    } catch (e: Exception) {
        "Error al obtener IP"
    }
}
