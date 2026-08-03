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
    /** Dispositivo de expansión del sistema: define cómo se interpreta la medición. */
    val tipoExpansion: TipoExpansion = TipoExpansion.NO_ESPECIFICADO,
    /** Objetivo ajustado a mano (°C) del parámetro guía; null = usar el automático. */
    val objetivoMinC: Double? = null,
    val objetivoMaxC: Double? = null,
    /** Nombres de los medidores que aportaron datos (trazabilidad). */
    val dispositivos: List<String> = emptyList(),
) {
    /** Rango objetivo manual si el técnico lo ajustó; null = usar el automático. */
    fun objetivoManual(): RangoObjetivo? =
        if (objetivoMinC != null && objetivoMaxC != null) RangoObjetivo(objetivoMinC, objetivoMaxC) else null
}
