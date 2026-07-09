package com.checkingcontainer.core.model

/**
 * Foto instantánea de las mediciones BLE en un momento dado, capturada desde la
 * pantalla de sensores hacia el estimado abierto del contenedor. Los campos son
 * anulables: una captura parcial (solo presiones, sin pinza de corriente, etc.)
 * es válida — `null` significa "sin dato" y el PDF lo muestra como "—".
 *
 * Unidades fijas: presiones en PSIG, temperaturas en °C, corriente en amperios.
 */
data class MedicionSnapshot(
    val timestamp: Long,
    val refrigerante: String = "",
    val presionAltaPsig: Double? = null,
    val presionBajaPsig: Double? = null,
    val satLiquidoC: Double? = null,
    val satVaporC: Double? = null,
    val superheatC: Double? = null,
    val subcoolingC: Double? = null,
    val tempSuccionC: Double? = null,
    val tempDescargaC: Double? = null,
    val corrienteA: Double? = null,
    /** Nombres de los medidores que aportaron datos (trazabilidad). */
    val dispositivos: List<String> = emptyList(),
)
