package com.diret.gpsremotetracker.state

import com.diret.gpsremotetracker.dt.SensorData
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Un objeto singleton para mantener y compartir el estado de la recolección de datos
 * a través de toda la aplicación, comunicando el servicio en segundo plano con la UI.
 */
object DataCollectionStateHolder {

    // Flujo que ahora emite el objeto SensorData completo.
    private val _lastDataSaved = MutableSharedFlow<SensorData>(replay = 1)
    val lastDataSaved = _lastDataSaved.asSharedFlow()

    /**
     * El servicio llama a esta función para notificar que se ha guardado un nuevo dato.
     * @param data El objeto SensorData completo que fue guardado.
     */
    suspend fun notifyDataSaved(data: SensorData) {
        _lastDataSaved.emit(data)
    }
}
