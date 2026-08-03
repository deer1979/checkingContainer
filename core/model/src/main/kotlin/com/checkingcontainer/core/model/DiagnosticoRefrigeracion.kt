package com.checkingcontainer.core.model

import java.util.Locale

/**
 * Tipo de dispositivo de expansión del sistema. Determina QUÉ parámetro sirve de
 * guía (subenfriamiento vs recalentamiento) y, por tanto, cómo se interpreta la
 * medición. Ver `PROTOCOLO_YJACK_BLE.md` y la matriz de diagnóstico más abajo.
 *
 * - Válvula termostática (TXV) y electrónica (EEV): la válvula controla el
 *   recalentamiento, así que la carga se juzga por el **subenfriamiento**.
 * - Restricción fija (tubo capilar / orificio / pistón): no hay control activo,
 *   la carga se juzga por el **recalentamiento**.
 */
enum class TipoExpansion(val label: String, val abreviatura: String) {
    EEV("Válvula de expansión electrónica", "EEV"),
    TXV("Válvula de expansión termostática", "TXV"),
    RESTRICCION_FIJA("Restricción fija (capilar / orificio)", "Restricción fija"),
    NO_ESPECIFICADO("Sin especificar", "—"),
}

/** Parámetro que sirve de guía para juzgar la carga según el dispositivo. */
enum class ParametroGuia { SUBENFRIAMIENTO, RECALENTAMIENTO, NINGUNO }

/** Rango objetivo [min, max] en °C para recalentamiento o subenfriamiento. */
data class RangoObjetivo(val min: Double, val max: Double) {
    fun contiene(v: Double): Boolean = v in min..max

    fun etiqueta(): String {
        fun f(v: Double) =
            if (v == v.toLong().toDouble()) v.toLong().toString()
            else String.format(Locale.US, "%.1f", v)
        return "${f(min)}–${f(max)} °C"
    }
}

/** Severidad de una observación del diagnóstico (mapea a los estados A/B/C del reporte). */
enum class Severidad { OK, ALERTA, INFO }

/** Una línea de diagnóstico técnico lista para mostrar en pantalla y en el PDF. */
data class Observacion(val severidad: Severidad, val texto: String)

/**
 * Rangos objetivo de referencia por tipo de expansión. Valores prácticos de
 * campo (°C). El subenfriamiento es la guía en TXV/EEV; el recalentamiento en
 * restricción fija. Se pueden reajustar a mano por equipo (override manual).
 */
object ObjetivoRefrigeracion {

    fun parametroGuia(tipo: TipoExpansion): ParametroGuia = when (tipo) {
        TipoExpansion.EEV, TipoExpansion.TXV -> ParametroGuia.SUBENFRIAMIENTO
        TipoExpansion.RESTRICCION_FIJA -> ParametroGuia.RECALENTAMIENTO
        TipoExpansion.NO_ESPECIFICADO -> ParametroGuia.NINGUNO
    }

    /** Rango objetivo de recalentamiento (superheat) automático, o null si no aplica. */
    fun objetivoRecalentamiento(tipo: TipoExpansion): RangoObjetivo? = when (tipo) {
        TipoExpansion.EEV, TipoExpansion.TXV -> RangoObjetivo(4.0, 7.0)
        TipoExpansion.RESTRICCION_FIJA -> RangoObjetivo(5.5, 11.0)
        TipoExpansion.NO_ESPECIFICADO -> null
    }

    /** Rango objetivo de subenfriamiento (subcooling) automático, o null si no aplica. */
    fun objetivoSubenfriamiento(tipo: TipoExpansion): RangoObjetivo? = when (tipo) {
        TipoExpansion.EEV, TipoExpansion.TXV -> RangoObjetivo(8.0, 12.0)
        TipoExpansion.RESTRICCION_FIJA -> null
        TipoExpansion.NO_ESPECIFICADO -> null
    }

    /**
     * Rangos efectivos a usar: los automáticos por dispositivo, salvo que haya un
     * [objetivoManual] que sobreescribe el parámetro GUÍA (lo que el técnico ajusta
     * según el manual del equipo).
     */
    fun efectivos(tipo: TipoExpansion, objetivoManual: RangoObjetivo?): ObjetivosEfectivos {
        val guia = parametroGuia(tipo)
        var sh = objetivoRecalentamiento(tipo)
        var sc = objetivoSubenfriamiento(tipo)
        if (objetivoManual != null) {
            when (guia) {
                ParametroGuia.RECALENTAMIENTO -> sh = objetivoManual
                ParametroGuia.SUBENFRIAMIENTO -> sc = objetivoManual
                ParametroGuia.NINGUNO -> {}
            }
        }
        return ObjetivosEfectivos(sh, sc, guia)
    }
}

data class ObjetivosEfectivos(
    val recalentamiento: RangoObjetivo?,
    val subenfriamiento: RangoObjetivo?,
    val guia: ParametroGuia,
) {
    /** El rango del parámetro guía (el que marca dentro/fuera en el reporte). */
    fun rangoGuia(): RangoObjetivo? = when (guia) {
        ParametroGuia.RECALENTAMIENTO -> recalentamiento
        ParametroGuia.SUBENFRIAMIENTO -> subenfriamiento
        ParametroGuia.NINGUNO -> null
    }
}

/**
 * Diagnóstico automático a partir de recalentamiento (superheat) + subenfriamiento
 * (subcooling), interpretados según el dispositivo de expansión. Devuelve UNA
 * observación (el hallazgo principal), con tono de ayuda ("posible / revisar"):
 *
 *  - TXV/EEV → matriz recalentamiento × subenfriamiento (el subcooling es la guía).
 *  - Restricción fija → solo recalentamiento (el subcooling no es confiable).
 *
 * Matriz clásica (con objetivo por dispositivo):
 *   SH alto + SC bajo  → carga baja
 *   SH bajo + SC alto  → exceso de carga
 *   SH alto + SC alto  → restricción / válvula subalimentando
 *   SH bajo + SC bajo  → sobrealimentación / compresión débil
 *   ambos en rango     → normal
 */
object DiagnosticoRefrigeracion {

    private const val CARGA_BAJA =
        "posible carga baja de refrigerante. Revisar fugas y completar carga."
    private const val EXCESO =
        "posible exceso de refrigerante. Recuperar carga sobrante."
    private const val RESTRICCION =
        "posible restricción en la línea de líquido o válvula subalimentando. " +
            "Revisar filtro secador y válvula de expansión."
    private const val SOBREALIM =
        "posible sobrealimentación de la válvula o compresión débil. " +
            "Revisar válvula de expansión y compresor."
    private const val OK_TXT =
        "Sistema dentro de parámetros normales para el refrigerante y el tipo de expansión."
    private const val SIN_DATOS =
        "Diagnóstico no disponible: mediciones incompletas."
    private const val SIN_TIPO =
        "Seleccione el tipo de expansión para obtener el diagnóstico automático."

    /** -1 = bajo, 0 = en rango, 1 = alto, null = sin dato / sin objetivo. */
    private fun estado(v: Double?, r: RangoObjetivo?): Int? {
        if (v == null || r == null) return null
        return when {
            v < r.min -> -1
            v > r.max -> 1
            else -> 0
        }
    }

    fun evaluar(
        tipo: TipoExpansion,
        recalentamientoC: Double?,
        subenfriamientoC: Double?,
        objetivos: ObjetivosEfectivos,
    ): Observacion {
        if (tipo == TipoExpansion.NO_ESPECIFICADO) return Observacion(Severidad.INFO, SIN_TIPO)

        val esh = estado(recalentamientoC, objetivos.recalentamiento)
        val esc = estado(subenfriamientoC, objetivos.subenfriamiento)

        // Restricción fija: se juzga SOLO por recalentamiento.
        if (tipo == TipoExpansion.RESTRICCION_FIJA) {
            return when (esh) {
                1 -> Observacion(Severidad.ALERTA, "Recalentamiento alto: $CARGA_BAJA")
                -1 -> Observacion(Severidad.ALERTA, "Recalentamiento bajo: $EXCESO")
                0 -> Observacion(Severidad.OK, OK_TXT)
                else -> Observacion(Severidad.INFO, SIN_DATOS)
            }
        }

        // TXV / EEV: matriz recalentamiento × subenfriamiento (guía = subenfriamiento).
        return when {
            esh == 1 && esc == -1 -> Observacion(Severidad.ALERTA, "Recalentamiento alto y subenfriamiento bajo: $CARGA_BAJA")
            esh == -1 && esc == 1 -> Observacion(Severidad.ALERTA, "Recalentamiento bajo y subenfriamiento alto: $EXCESO")
            esh == 1 && esc == 1 -> Observacion(Severidad.ALERTA, "Recalentamiento y subenfriamiento altos: $RESTRICCION")
            esh == -1 && esc == -1 -> Observacion(Severidad.ALERTA, "Recalentamiento y subenfriamiento bajos: $SOBREALIM")
            esc == -1 -> Observacion(Severidad.ALERTA, "Subenfriamiento bajo: $CARGA_BAJA")
            esc == 1 -> Observacion(Severidad.ALERTA, "Subenfriamiento alto: $EXCESO")
            esc == 0 && esh == 1 -> Observacion(Severidad.ALERTA, "Subenfriamiento correcto pero recalentamiento alto: revisar evaporador, flujo de aire y filtros.")
            esc == 0 && esh == -1 -> Observacion(Severidad.ALERTA, "Subenfriamiento correcto pero recalentamiento bajo: riesgo de líquido al compresor, revisar la válvula de expansión.")
            esc == 0 -> Observacion(Severidad.OK, OK_TXT)
            esc == null && esh == 1 -> Observacion(Severidad.ALERTA, "Recalentamiento alto: $CARGA_BAJA")
            esc == null && esh == -1 -> Observacion(Severidad.ALERTA, "Recalentamiento bajo: $SOBREALIM")
            esc == null && esh == 0 -> Observacion(Severidad.OK, OK_TXT)
            else -> Observacion(Severidad.INFO, SIN_DATOS)
        }
    }

    /** Conveniencia: evalúa directamente una medición capturada. */
    fun evaluar(m: MedicionSnapshot): Observacion {
        val objetivos = ObjetivoRefrigeracion.efectivos(m.tipoExpansion, m.objetivoManual())
        return evaluar(m.tipoExpansion, m.superheatC, m.subcoolingC, objetivos)
    }
}
