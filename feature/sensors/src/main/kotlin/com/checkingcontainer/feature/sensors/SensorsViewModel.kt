package com.checkingcontainer.feature.sensors

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkingcontainer.core.domain.EstimadosRepository
import com.checkingcontainer.core.model.DiagnosticoRefrigeracion
import com.checkingcontainer.core.model.MedicionSnapshot
import com.checkingcontainer.core.model.Observacion
import com.checkingcontainer.core.model.ObjetivoRefrigeracion
import com.checkingcontainer.core.model.ObjetivosEfectivos
import com.checkingcontainer.core.model.RangoObjetivo
import com.checkingcontainer.core.model.Saturacion
import com.checkingcontainer.core.model.TipoExpansion
import com.checkingcontainer.feature.sensors.navigation.SENSORS_CONTAINER_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
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
    // Dispositivo de expansión + objetivo (automático por dispositivo, reajustable a mano).
    val tipoExpansion: TipoExpansion = TipoExpansion.NO_ESPECIFICADO,
    val objetivoManual: RangoObjetivo? = null,
    // Captura hacia el estimado abierto del contenedor
    val capturando: Boolean = false,
    val capturaMensaje: String? = null,
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
    // OJO: la tabla PT (vapSatPressures/liqSatPressures) está en presión ABSOLUTA
    // (psia), NO manométrica — por eso se pasa la presión cruda del sensor tal
    // cual (también absoluta), sin restar la atmósfera. Con aPsig() antes se
    // buscaba a ~0 y caía fuera de la tabla, dando "—" incluso conectado.
    val satVaporC get() = Saturacion.satTempC(vapSatPressures, vapSatGas, presionBajaRaw)
    val satLiquidoC get() = Saturacion.satTempC(liqSatPressures, liqSatGas, presionAltaRaw)
    val superheatC get() = Saturacion.superheat(YjackParser.aCelsius(tempSuccionRaw), satVaporC)
    val subcoolingC get() = Saturacion.subcooling(satLiquidoC, YjackParser.aCelsius(tempDescargaRaw))

    /**
     * Hay presión conectada pero la saturación no calcula → casi siempre porque
     * el sensor está al aire (≈0 PSIG), fuera del rango útil del gas. Sirve para
     * avisar que hay que conectar a un sistema con presión (no es un error).
     */
    val presionFueraDeRango: Boolean
        get() = presion != null &&
            satVaporC == SensorReading.SIN_DATO &&
            satLiquidoC == SensorReading.SIN_DATO

    // Recalentamiento / subenfriamiento en null cuando no hay dato (para el diagnóstico).
    private val recalentamientoONull get() = superheatC.oNull()
    private val subenfriamientoONull get() = subcoolingC.oNull()

    /** Rangos objetivo efectivos (automáticos por dispositivo o el ajuste manual). */
    val objetivos: ObjetivosEfectivos
        get() = ObjetivoRefrigeracion.efectivos(tipoExpansion, objetivoManual)

    /** Diagnóstico técnico en vivo (mismo cálculo que va al PDF). */
    val observacion: Observacion
        get() = DiagnosticoRefrigeracion.evaluar(
            tipoExpansion, recalentamientoONull, subenfriamientoONull, objetivos,
        )

    /** true/false/null (sin dato u objetivo) según el recalentamiento caiga en rango. */
    val recalentamientoEnRango: Boolean?
        get() = objetivos.recalentamiento?.let { r -> recalentamientoONull?.let { r.contiene(it) } }
    val subenfriamientoEnRango: Boolean?
        get() = objetivos.subenfriamiento?.let { r -> subenfriamientoONull?.let { r.contiene(it) } }
}

@HiltViewModel
class SensorsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val scanner: BleSensorScanner,
    private val refrigerantes: RefrigerantRepo,
    private val estimadosRepo: EstimadosRepository,
) : ViewModel() {

    private val containerNo: String = savedStateHandle[SENSORS_CONTAINER_ARG] ?: ""

    private val _state = MutableStateFlow(SensorsUiState(containerNo = containerNo))
    val state: StateFlow<SensorsUiState> = _state.asStateFlow()

    private var scanJob: Job? = null
    private var muestreoJob: Job? = null

    /**
     * Identifica la sesión de escaneo en curso. Al detener (o reiniciar) se
     * incrementa, de modo que un job viejo que aún esté terminando no pisa el
     * estado de la sesión nueva.
     *
     * No lleva sincronización a propósito: solo se lee y escribe desde
     * `viewModelScope`, que despacha en `Main.immediate`, así que todos los
     * accesos ocurren en el hilo principal. Si alguna vez se mueve el escaneo a
     * otro dispatcher, esto necesita volverse atómico.
     */
    private var scanGeneration = 0L

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

    /** Cambia el dispositivo de expansión (define cómo se interpreta la medición). */
    fun seleccionarTipoExpansion(tipo: TipoExpansion) {
        _state.update { it.copy(tipoExpansion = tipo) }
    }

    /** Ajuste manual del objetivo del parámetro guía (según el manual del equipo). */
    fun ajustarObjetivo(min: Double, max: Double) {
        if (min.isNaN() || max.isNaN() || max < min) return
        _state.update { it.copy(objetivoManual = RangoObjetivo(min, max)) }
    }

    /** Vuelve al objetivo automático por dispositivo. */
    fun limpiarObjetivoManual() {
        _state.update { it.copy(objetivoManual = null) }
    }

    fun toggleEscaneo() {
        if (_state.value.escaneando) detener() else iniciar()
    }

    private fun iniciar() {
        if (_state.value.escaneando || scanJob?.isActive == true || muestreoJob?.isActive == true) {
            return
        }
        if (!scanner.bluetoothDisponible()) {
            _state.update { it.copy(bluetoothApagado = true, escaneando = false) }
            return
        }

        val generation = ++scanGeneration
        _state.update { it.copy(escaneando = true, bluetoothApagado = false) }

        val newScanJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
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
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // El estado final se normaliza en finally. La UI ya distingue
                // Bluetooth apagado de un flujo que terminó sin lecturas.
            } finally {
                if (scanGeneration == generation) {
                    val currentMuestreo = muestreoJob
                    muestreoJob = null
                    currentMuestreo?.cancel()
                    scanJob = null
                    _state.update {
                        it.copy(
                            escaneando = false,
                            bluetoothApagado = !scanner.bluetoothDisponible(),
                            tarjetas = emptyMap(),
                        )
                    }
                }
            }
        }

        scanJob = newScanJob
        muestreoJob = viewModelScope.launch {
            // Muestreo cada 5 s: registra la lectura actual en el historial
            // (5 tomas = 25 s). Una sesión vieja nunca modifica la nueva.
            while (isActive && scanGeneration == generation) {
                delay(INTERVALO_MUESTREO_MS)
                if (scanGeneration != generation) break
                _state.update { s ->
                    s.copy(
                        tarjetas = s.tarjetas.mapValues { (_, t) ->
                            t.copy(historial = (t.historial + t.ultima).takeLast(MAX_TOMAS))
                        },
                    )
                }
            }
        }
        newScanJob.start()
    }

    fun detener() {
        scanGeneration += 1
        val currentScan = scanJob
        val currentMuestreo = muestreoJob
        scanJob = null
        muestreoJob = null
        currentScan?.cancel()
        currentMuestreo?.cancel()
        // Limpiar las tarjetas al detener: las lecturas dejan de ser válidas.
        _state.update { it.copy(escaneando = false, tarjetas = emptyMap()) }
    }

    /**
     * Congela las lecturas actuales en un [MedicionSnapshot] y lo agrega al
     * estimado ABIERTO del contenedor. Captura parcial válida: lo que no esté
     * midiendo queda como null ("—" en el PDF).
     */
    fun capturarParaEstimado() {
        val s = _state.value
        if (!s.hayDatos || s.containerNo.isEmpty() || s.capturando) return
        viewModelScope.launch {
            _state.update { it.copy(capturando = true) }
            val snapshot = MedicionSnapshot(
                timestamp = System.currentTimeMillis(),
                refrigerante = s.refrigerante,
                presionAltaPsig = YjackParser.aPsig(s.presionAltaRaw).oNull(),
                presionBajaPsig = YjackParser.aPsig(s.presionBajaRaw).oNull(),
                satLiquidoC = s.satLiquidoC.oNull(),
                satVaporC = s.satVaporC.oNull(),
                superheatC = s.superheatC.oNull(),
                subcoolingC = s.subcoolingC.oNull(),
                tempSuccionC = YjackParser.aCelsius(s.tempSuccionRaw).oNull(),
                tempDescargaC = YjackParser.aCelsius(s.tempDescargaRaw).oNull(),
                corrienteA = s.corriente?.ultima?.valor1.oNull(),
                tipoExpansion = s.tipoExpansion,
                objetivoMinC = s.objetivoManual?.min,
                objetivoMaxC = s.objetivoManual?.max,
                dispositivos = s.tarjetas.values.map { it.deviceName }.distinct(),
            )
            val ok = runCatching { estimadosRepo.addMedicion(s.containerNo, snapshot) }
                .getOrDefault(false)
            _state.update {
                it.copy(
                    capturando = false,
                    capturaMensaje = if (ok) {
                        "Medición capturada en el estimado"
                    } else {
                        "No hay estimado abierto para ${s.containerNo}"
                    },
                )
            }
        }
    }

    fun consumirMensajeCaptura() {
        _state.update { it.copy(capturaMensaje = null) }
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

/** SIN_DATO/NaN → null (el snapshot guarda solo lo realmente medido). */
private fun Double?.oNull(): Double? =
    this?.takeUnless { it == SensorReading.SIN_DATO || it.isNaN() }

/** Clave estable de rol por lectura. */
fun claveRol(deviceName: String, tipo: SensorType, index: Int): String = "$deviceName#$tipo#$index"
