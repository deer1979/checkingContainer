package com.checkingcontainer.feature.sensors

/**
 * Rol que el técnico asigna a cada lectura (los dos sensores de presión/temperatura
 * son idénticos y no se distinguen solos). ALTA = descarga/líquido (rojo);
 * BAJA = succión/vapor (celeste).
 */
enum class RolMedicion { ALTA, BAJA }

/** Tipo de medición que entrega un sensor BLE de la familia Yellow Jacket. */
enum class SensorType {
    /** TITANMAX / P51: las dos presiones (manométricas, PSI). */
    PRESION,
    /** TITANMAX temperatura / pinza de temperatura (°C). */
    TEMPERATURA,
    /** Vacuómetro (micrones). */
    VACIO,
    /** Amperímetro (A). */
    CORRIENTE,
    DESCONOCIDO,
}

/**
 * Una lectura decodificada de un sensor. `valor1`/`valor2`/`valor3` mapean a
 * sensor1/2/3 del protocolo (p. ej. en presión: valor1 = presión 1, valor2 =
 * presión 2). El significado depende de [type]. Las unidades quedan pendientes
 * de confirmar con lectura real (ver PROTOCOLO_YJACK_BLE.md).
 */
data class SensorReading(
    /** Nombre que anuncia el dispositivo por BLE (marca + modelo), p. ej. "TITAN-2503-5221". */
    val deviceName: String,
    val type: SensorType,
    val valor1: Double,
    val valor2: Double,
    val valor3: Double = SIN_DATO,
    /** Batería 0..100 (decenas, según el protocolo). */
    val bateria: Int = 0,
    val rssi: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
) {
    val tieneValor1: Boolean get() = valor1 != SIN_DATO
    val tieneValor2: Boolean get() = valor2 != SIN_DATO
    val tieneValor3: Boolean get() = valor3 != SIN_DATO

    companion object {
        /** Centinela "sin dato" del protocolo YJACK (32767/10 y SFLOAT 3276.7). */
        const val SIN_DATO = 3276.7
    }
}
