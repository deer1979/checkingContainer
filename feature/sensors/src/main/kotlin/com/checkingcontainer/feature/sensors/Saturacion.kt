package com.checkingcontainer.feature.sensors

/**
 * Cálculos de saturación / superheat / subcooling a partir de la tabla PT del
 * refrigerante. Portado de `Model.getVaporsSatTemp/getLiquidSatTemp` de la app
 * oficial YJACK VIEW: interpolación lineal entre los dos puntos de la tabla que
 * encierran la presión leída. Las temperaturas de la tabla vienen en **°F ×10**;
 * el resultado se entrega en **°C**. `-300 °F` es el centinela de "fuera de rango".
 *
 * Funciones puras (sin Android) para poder testearlas.
 */
object Saturacion {

    private const val MIN_SAT_F = -300.0
    private val SIN = SensorReading.SIN_DATO

    /**
     * Temperatura de saturación (°C) para [readingPsig] (manométrica) usando el
     * eje de presiones [axis] y las temperaturas [tempsX10] (°F ×10) del gas.
     * Devuelve [SensorReading.SIN_DATO] si está fuera de rango o sin dato.
     */
    fun satTempC(axis: List<Double>, tempsX10: List<Int>, readingPsig: Double): Double {
        if (readingPsig == SIN) return SIN
        var d2 = SIN; var d3 = SIN; var d4 = SIN; var d = SIN
        for (i in axis.indices) {
            if (readingPsig < axis[i]) {
                d2 = axis[i]
                d3 = tempsX10[i] / 10.0
                if (i != 0) {
                    d4 = axis[i - 1]
                    d = tempsX10[i - 1] / 10.0
                } else {
                    d = SIN; d4 = SIN
                }
                break
            }
        }
        if (d == SIN || d2 == SIN || d3 == SIN || d4 == SIN) return SIN
        if (d <= MIN_SAT_F || d3 <= MIN_SAT_F) return SIN
        val tempF = ((readingPsig - d4) / (d2 - d4)) * (d3 - d) + d
        return (tempF - 32.0) * 5.0 / 9.0
    }

    /** Sobrecalentamiento (°C): temp de succión medida − temp de saturación de vapor. */
    fun superheat(tempSuccionC: Double, vaporSatC: Double): Double =
        if (tempSuccionC == SIN || vaporSatC == SIN) SIN else tempSuccionC - vaporSatC

    /** Subenfriamiento (°C): temp de saturación de líquido − temp de líquido medida. */
    fun subcooling(liquidSatC: Double, tempLiquidoC: Double): Double =
        if (liquidSatC == SIN || tempLiquidoC == SIN) SIN else liquidSatC - tempLiquidoC
}
