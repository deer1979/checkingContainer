package com.checkingcontainer.feature.sensors

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkingcontainer.feature.sensors.navigation.SENSORS_CONTAINER_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    // rol asignado por lectura: clave = deviceName#tipo#índice (0 = valor1, 1 = valor2)
    val roles: Map<String, RolMedicion> = emptyMap(),
    // Refrigerante seleccionado + su tabla PT (para saturación/superheat/subcooling).
    val refrigerantes: List<String> = emptyList(),
    val refrigerante: String = "",
    val vapSatPressures: List<Double> = emptyList(),
    val liqSatPressures: List<Double> = emptyList(),
    val vapSatGas: List<Int> = emptyList(),
    val liqSatGas: List<Int> = emptyList(),
) {
    /** Rol asignado; por defecto el primer sensor es ALTA y el segundo BAJA. */
    fun rolDe(deviceName: String, tipo: SensorType, index: Int): RolMedicion =
        roles[claveRol(deviceName, tipo, index)] ?: if (index == 0) RolMedicion.ALTA else RolMedicion.BAJA
    val presiones get() = tarjetas.values.filter { it.ultima.type == SensorType.PRESION }
    val temperaturas get() = tarjetas.values.filter { it.ultima.type == SensorType.TEMPERATURA }
    val corrientes get() = tarjetas.values.filter { it.ultima.type == SensorType.CORRIENTE }
    // Primer sensor de cada tipo (la pantalla muestra tarjetas fijas; null = sin datos).
    val presion get() = presiones.firstOrNull()
    val temperatura get() = temperaturas.firstOrNull()
    val corriente get() = corrientes.firstOrNull()
    val hayDatos get() = tarjetas.isNotEmpty()

    /** Valor crudo (sin convertir) de la lectura [t] cuyo rol es [rol], o SIN_DATO. */
    private fun valorPorRol(t: TarjetaSensor?, tipo: SensorType, rol: RolMedicion): Double {
        if (t == null) return SensorReading.SIN_DATO
        for (idx in 0..1) {
            if (rolDe(t.deviceName, tipo, idx) == rol) {
                return if (idx == 0) t.ultima.valor1 else t.ultima.valor2
            }
        }
        return SensorReading.SIN_DATO
    }

    // Entradas crudas por lado (presión absoluta del equipo, temp en °F).
    val presionAltaRaw get() = valorPorRol(presion, SensorType.PRESION, RolMedicion.ALTA)
    val presionBajaRaw get() = valorPorRol(presion, SensorType.PRESION, RolMedicion.BAJA)
    val tempDescargaRaw get() = valorPorRol(temperatura, SensorType.TEMPERATURA, RolMedicion.ALTA)
    val tempSuccionRaw get() = valorPorRol(temperatura, SensorType.TEMPERATURA, RolMedicion.BAJA)

    // Saturación, superheat y subcooling (°C) calculados con la tabla del gas.
    val satVaporC get() = Saturacion.satTempC(vapSatPressures, vapSatGas, YjackParser.aPsig(presionBajaRaw))
    val satLiquidoC get() = Saturacion.satTempC(liqSatPressures, liqSatGas, YjackParser.aPsig(presionAltaRaw))
    val superheatC get() = Saturacion.superheat(YjackParser.aCelsius(tempSuccionRaw), satVaporC)
    val subcoolingC get() = Saturacion.subcooling(satLiquidoC, YjackParser.aCelsius(tempDescargaRaw))
}

@HiltViewModel
class SensorsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val scanner: BleSensorScanner,
    private val refrigerantes: RefrigerantRepo,
) : ViewModel() {

    private val containerNo: String = savedStateHandle[SENSORS_CONTAINER_ARG] ?: ""

    private val _state = MutableStateFlow(SensorsUiState(containerNo = containerNo))
    val state: StateFlow<SensorsUiState> = _state.asStateFlow()

    private var scanJob: Job? = null
    private var muestreoJob: Job? = null

    init {
        val nombres = refrigerantes.nombres
        val porDefecto = if ("R-134a" in nombres) "R-134a" else nombres.firstOrNull() ?: ""
        _state.update { it.copy(refrigerantes = nombres) }
        seleccionarRefrigerante(porDefecto)
    }

    /** Cambia el gas seleccionado y carga su tabla PT en el estado. */
    fun seleccionarRefrigerante(nombre: String) {
        val gas = refrigerantes.gas(nombre)
        _state.update {
            it.copy(
                refrigerante = nombre,
                vapSatPressures = refrigerantes.vapSatPressures,
                liqSatPressures = refrigerantes.liqSatPressures,
                vapSatGas = gas?.vapSat ?: emptyList(),
                liqSatGas = gas?.liqSat ?: emptyList(),
            )
        }
    }

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
                // Cada anuncio actualiza solo el valor EN VIVO (número grande).
                _state.update { s ->
                    val clave = "${lectura.deviceName}-${lectura.type}"
                    val previa = s.tarjetas[clave]
                    val tarjeta = previa?.copy(ultima = lectura)
                        ?: TarjetaSensor(lectura.deviceName, lectura, emptyList())
                    s.copy(tarjetas = s.tarjetas + (clave to tarjeta))
                }
            }
        }
        // Muestreo cada 5 s: registra la lectura actual en el historial (5 tomas = 25 s).
        // Empieza vacío y se va llenando una muestra a la vez.
        muestreoJob = viewModelScope.launch {
            while (isActive) {
                delay(INTERVALO_MUESTREO_MS)
                _state.update { s ->
                    s.copy(
                        tarjetas = s.tarjetas.mapValues { (_, t) ->
                            t.copy(historial = (t.historial + t.ultima).takeLast(MAX_TOMAS))
                        },
                    )
                }
            }
        }
    }

    fun detener() {
        scanJob?.cancel()
        scanJob = null
        muestreoJob?.cancel()
        muestreoJob = null
        // Limpiar las tarjetas al detener: las lecturas dejan de ser válidas.
        _state.update { it.copy(escaneando = false, tarjetas = emptyMap()) }
    }

    /** Alterna ALTA <-> BAJA para una lectura concreta. */
    fun toggleRol(deviceName: String, tipo: SensorType, index: Int) {
        val clave = claveRol(deviceName, tipo, index)
        _state.update { s ->
            val actual = s.rolDe(deviceName, tipo, index)
            val nuevo = if (actual == RolMedicion.ALTA) RolMedicion.BAJA else RolMedicion.ALTA
            s.copy(roles = s.roles + (clave to nuevo))
        }
    }

    override fun onCleared() {
        detener()
        super.onCleared()
    }

    private companion object {
        const val MAX_TOMAS = 5
        const val INTERVALO_MUESTREO_MS = 5_000L
    }
}

/** Clave estable de rol por lectura. */
fun claveRol(deviceName: String, tipo: SensorType, index: Int): String = "$deviceName#$tipo#$index"
