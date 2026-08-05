package com.checkingcontainer.core.model

/**
 * Centinela de "sin dato" para las lecturas de sensores y los cálculos derivados.
 *
 * Es el valor que emiten los medidores YJACK/TITAN cuando un canal no tiene
 * lectura, y se propaga por toda la cadena de cálculo para no confundir un cero
 * real (0 PSI, 0 °C son medidas válidas) con la ausencia de medición.
 */
const val SIN_DATO: Double = 3276.7

/**
 * Cálculos de saturación / superheat / subcooling a partir de la tabla PT del
 * refrigerante. Portado de `Model.getVaporsSatTemp/getLiquidSatTemp` de la app
 * oficial YJACK VIEW: interpolación lineal entre los dos puntos de la tabla que
 * encierran la presión leída. Las temperaturas de la tabla vienen en **°F ×10**;
 * el resultado se entrega en **°C**. `-300 °F` es el centinela de "fuera de rango".
 *
 * OJO: la tabla PT está en presión **ABSOLUTA** (psia), no manométrica. Hay que
 * pasar la lectura cruda del sensor tal cual, sin restar la atmósfera.
 *
 * Vive en `core:model` (JVM puro, sin Android) junto a [DiagnosticoRefrigeracion]:
 * todo el dominio termodinámico en un solo sitio y testeable sin emulador. La
 * carga del JSON de refrigerantes se queda en `feature:sensors` porque necesita
 * recursos de Android.
 */
object Saturacion {

    private const val MIN_SAT_F = -300.0

    /**
     * Temperatura de saturación (°C) para [readingPsia] (ABSOLUTA) usando el eje
     * de presiones [axis] y las temperaturas [tempsX10] (°F ×10) del gas.
     * Devuelve [SIN_DATO] si está fuera de rango o sin dato.
     */
    fun satTempC(axis: List<Double>, tempsX10: List<Int>, readingPsia: Double): Double {
        if (readingPsia == SIN_DATO) return SIN_DATO
        var d2 = SIN_DATO
        var d3 = SIN_DATO
        var d4 = SIN_DATO
        var d = SIN_DATO
        for (i in axis.indices) {
            if (readingPsia < axis[i]) {
                d2 = axis[i]
                d3 = tempsX10[i] / 10.0
                if (i != 0) {
                    d4 = axis[i - 1]
                    d = tempsX10[i - 1] / 10.0
                } else {
                    d = SIN_DATO
                    d4 = SIN_DATO
                }
                break
            }
        }
        if (d == SIN_DATO || d2 == SIN_DATO || d3 == SIN_DATO || d4 == SIN_DATO) return SIN_DATO
        if (d <= MIN_SAT_F || d3 <= MIN_SAT_F) return SIN_DATO
        val tempF = ((readingPsia - d4) / (d2 - d4)) * (d3 - d) + d
        return (tempF - 32.0) * 5.0 / 9.0
    }

    /** Sobrecalentamiento (°C): temp de succión medida − temp de saturación de vapor. */
    fun superheat(tempSuccionC: Double, vaporSatC: Double): Double =
        if (tempSuccionC == SIN_DATO || vaporSatC == SIN_DATO) SIN_DATO else tempSuccionC - vaporSatC

    /** Subenfriamiento (°C): temp de saturación de líquido − temp de líquido medida. */
    fun subcooling(liquidSatC: Double, tempLiquidoC: Double): Double =
        if (liquidSatC == SIN_DATO || tempLiquidoC == SIN_DATO) SIN_DATO else liquidSatC - tempLiquidoC
}
