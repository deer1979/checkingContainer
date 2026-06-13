package com.checkingcontainer.feature.sensors

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkingcontainer.feature.sensors.navigation.SENSORS_CONTAINER_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado por sensor: la última lectura + las últimas N tomas (tendencia). */
data class TarjetaSensor(
    val deviceName: String,
    val ultima: SensorReading,
    val historial: List<SensorReading> = emptyList(),
)

data class SensorsUiState(
    val containerNo: String = "",
    val escaneando: Boolean = false,
    val bluetoothApagado: Boolean = false,
    // clave = deviceName + tipo, para no mezclar sensores distintos
    val tarjetas: Map<String, TarjetaSensor> = emptyMap(),
) {
    val presiones get() = tarjetas.values.filter { it.ultima.type == SensorType.PRESION }
    val temperaturas get() = tarjetas.values.filter { it.ultima.type == SensorType.TEMPERATURA }
    val corrientes get() = tarjetas.values.filter { it.ultima.type == SensorType.CORRIENTE }
    val hayDatos get() = tarjetas.isNotEmpty()
}

@HiltViewModel
class SensorsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val scanner: BleSensorScanner,
) : ViewModel() {

    private val containerNo: String = savedStateHandle[SENSORS_CONTAINER_ARG] ?: ""

    private val _state = MutableStateFlow(SensorsUiState(containerNo = containerNo))
    val state: StateFlow<SensorsUiState> = _state.asStateFlow()

    private var scanJob: Job? = null

    fun toggleEscaneo() {
        if (_state.value.escaneando) detener() else iniciar()
    }

    private fun iniciar() {
        if (!scanner.bluetoothDisponible()) {
            _state.update { it.copy(bluetoothApagado = true) }
            return
        }
        _state.update { it.copy(escaneando = true, bluetoothApagado = false) }
        scanJob = viewModelScope.launch {
            scanner.observe().collect { lectura ->
                _state.update { s ->
                    val clave = "${lectura.deviceName}-${lectura.type}"
                    val previa = s.tarjetas[clave]
                    val historial = ((previa?.historial ?: emptyList()) + lectura).takeLast(MAX_TOMAS)
                    s.copy(
                        tarjetas = s.tarjetas + (clave to TarjetaSensor(lectura.deviceName, lectura, historial)),
                    )
                }
            }
        }
    }

    fun detener() {
        scanJob?.cancel()
        scanJob = null
        _state.update { it.copy(escaneando = false) }
    }

    override fun onCleared() {
        detener()
        super.onCleared()
    }

    private companion object {
        const val MAX_TOMAS = 5
    }
}
