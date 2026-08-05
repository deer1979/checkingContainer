package com.checkingcontainer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La salida de este diagnóstico le dice a un técnico si debe AÑADIR o RECUPERAR
 * refrigerante. Un signo invertido manda a vaciar un equipo que estaba bajo de
 * carga, así que la matriz completa se cubre caso por caso.
 *
 * Objetivos automáticos vigentes:
 *  - EEV / TXV        → recalentamiento 4–7 °C, subenfriamiento 8–12 °C (guía: SC)
 *  - Restricción fija → recalentamiento 5,5–11 °C (guía: SH, sin objetivo de SC)
 */
class DiagnosticoRefrigeracionTest {

    private fun evaluar(
        tipo: TipoExpansion,
        sh: Double?,
        sc: Double?,
        manual: RangoObjetivo? = null,
    ): Observacion = DiagnosticoRefrigeracion.evaluar(
        tipo,
        sh,
        sc,
        ObjetivoRefrigeracion.efectivos(tipo, manual),
    )

    // ── Válvula (EEV/TXV): la guía es el subenfriamiento ────────────────────

    @Test
    fun `recalentamiento alto con subenfriamiento bajo indica carga baja`() {
        val obs = evaluar(TipoExpansion.EEV, sh = 12.0, sc = 3.0)
        assertEquals(Severidad.ALERTA, obs.severidad)
        assertTrue(obs.texto.contains("carga baja"))
    }

    @Test
    fun `recalentamiento bajo con subenfriamiento alto indica exceso de carga`() {
        val obs = evaluar(TipoExpansion.EEV, sh = 1.0, sc = 18.0)
        assertEquals(Severidad.ALERTA, obs.severidad)
        assertTrue(obs.texto.contains("exceso"))
    }

    @Test
    fun `ambos altos apuntan a restriccion en la linea de liquido`() {
        val obs = evaluar(TipoExpansion.TXV, sh = 14.0, sc = 16.0)
        assertEquals(Severidad.ALERTA, obs.severidad)
        assertTrue(obs.texto.contains("restricción"))
    }

    @Test
    fun `ambos bajos apuntan a sobrealimentacion o compresion debil`() {
        val obs = evaluar(TipoExpansion.TXV, sh = 1.0, sc = 2.0)
        assertEquals(Severidad.ALERTA, obs.severidad)
        assertTrue(obs.texto.contains("sobrealimentación"))
    }

    @Test
    fun `todo en rango reporta sistema normal`() {
        val obs = evaluar(TipoExpansion.EEV, sh = 5.5, sc = 10.0)
        assertEquals(Severidad.OK, obs.severidad)
    }

    @Test
    fun `los limites del rango se consideran dentro`() {
        // Frontera inclusiva: 4,0 y 7,0 en SH; 8,0 y 12,0 en SC.
        assertEquals(Severidad.OK, evaluar(TipoExpansion.EEV, sh = 4.0, sc = 8.0).severidad)
        assertEquals(Severidad.OK, evaluar(TipoExpansion.EEV, sh = 7.0, sc = 12.0).severidad)
    }

    @Test
    fun `subenfriamiento correcto con recalentamiento alto avisa del evaporador`() {
        val obs = evaluar(TipoExpansion.EEV, sh = 12.0, sc = 10.0)
        assertEquals(Severidad.ALERTA, obs.severidad)
        assertTrue(obs.texto.contains("evaporador"))
    }

    @Test
    fun `subenfriamiento correcto con recalentamiento bajo avisa de liquido al compresor`() {
        val obs = evaluar(TipoExpansion.EEV, sh = 1.0, sc = 10.0)
        assertEquals(Severidad.ALERTA, obs.severidad)
        assertTrue(obs.texto.contains("líquido al compresor"))
    }

    @Test
    fun `sin subenfriamiento se decide por el recalentamiento`() {
        assertTrue(evaluar(TipoExpansion.EEV, sh = 12.0, sc = null).texto.contains("carga baja"))
        assertTrue(evaluar(TipoExpansion.EEV, sh = 1.0, sc = null).texto.contains("sobrealimentación"))
        assertEquals(Severidad.OK, evaluar(TipoExpansion.EEV, sh = 5.0, sc = null).severidad)
    }

    // ── Restricción fija: la guía es el recalentamiento ─────────────────────

    @Test
    fun `restriccion fija juzga solo por recalentamiento`() {
        assertTrue(evaluar(TipoExpansion.RESTRICCION_FIJA, sh = 15.0, sc = null).texto.contains("carga baja"))
        assertTrue(evaluar(TipoExpansion.RESTRICCION_FIJA, sh = 2.0, sc = null).texto.contains("exceso"))
        assertEquals(Severidad.OK, evaluar(TipoExpansion.RESTRICCION_FIJA, sh = 8.0, sc = null).severidad)
    }

    @Test
    fun `restriccion fija ignora el subenfriamiento aunque este fuera de rango`() {
        // En capilar/orificio el subcooling no es indicador fiable: no debe
        // cambiar el veredicto si el recalentamiento está correcto.
        val obs = evaluar(TipoExpansion.RESTRICCION_FIJA, sh = 8.0, sc = 40.0)
        assertEquals(Severidad.OK, obs.severidad)
    }

    // ── Casos sin datos / sin configurar ────────────────────────────────────

    @Test
    fun `sin tipo de expansion pide seleccionarlo en vez de diagnosticar`() {
        val obs = evaluar(TipoExpansion.NO_ESPECIFICADO, sh = 5.0, sc = 10.0)
        assertEquals(Severidad.INFO, obs.severidad)
        assertTrue(obs.texto.contains("tipo de expansión"))
    }

    @Test
    fun `sin mediciones informa que el diagnostico no esta disponible`() {
        val obs = evaluar(TipoExpansion.EEV, sh = null, sc = null)
        assertEquals(Severidad.INFO, obs.severidad)
        assertTrue(obs.texto.contains("incompletas"))
    }

    // ── Objetivos por dispositivo y ajuste manual ───────────────────────────

    @Test
    fun `la guia depende del dispositivo de expansion`() {
        assertEquals(ParametroGuia.SUBENFRIAMIENTO, ObjetivoRefrigeracion.parametroGuia(TipoExpansion.EEV))
        assertEquals(ParametroGuia.SUBENFRIAMIENTO, ObjetivoRefrigeracion.parametroGuia(TipoExpansion.TXV))
        assertEquals(ParametroGuia.RECALENTAMIENTO, ObjetivoRefrigeracion.parametroGuia(TipoExpansion.RESTRICCION_FIJA))
        assertEquals(ParametroGuia.NINGUNO, ObjetivoRefrigeracion.parametroGuia(TipoExpansion.NO_ESPECIFICADO))
    }

    @Test
    fun `restriccion fija no publica objetivo de subenfriamiento`() {
        val objetivos = ObjetivoRefrigeracion.efectivos(TipoExpansion.RESTRICCION_FIJA, null)
        assertNull(objetivos.subenfriamiento)
        assertEquals(RangoObjetivo(5.5, 11.0), objetivos.rangoGuia())
    }

    @Test
    fun `el ajuste manual sobreescribe solo el parametro guia`() {
        val manual = RangoObjetivo(6.0, 9.0)

        // En válvula la guía es el subenfriamiento: el manual va ahí.
        val valvula = ObjetivoRefrigeracion.efectivos(TipoExpansion.EEV, manual)
        assertEquals(manual, valvula.subenfriamiento)
        assertEquals(RangoObjetivo(4.0, 7.0), valvula.recalentamiento)

        // En restricción fija la guía es el recalentamiento.
        val fija = ObjetivoRefrigeracion.efectivos(TipoExpansion.RESTRICCION_FIJA, manual)
        assertEquals(manual, fija.recalentamiento)
        assertNull(fija.subenfriamiento)
    }

    @Test
    fun `el objetivo manual cambia el veredicto`() {
        // 14 °C de subenfriamiento es exceso con el objetivo automático (8–12)…
        assertTrue(evaluar(TipoExpansion.EEV, sh = 5.0, sc = 14.0).texto.contains("exceso"))
        // …y correcto si el manual del equipo pide 12–16.
        assertEquals(
            Severidad.OK,
            evaluar(TipoExpansion.EEV, sh = 5.0, sc = 14.0, manual = RangoObjetivo(12.0, 16.0)).severidad,
        )
    }

    @Test
    fun `evaluar sobre una medicion capturada usa su tipo y objetivo guardados`() {
        val medicion = MedicionSnapshot(
            timestamp = 0L,
            refrigerante = "R-134a",
            superheatC = 12.0,
            subcoolingC = 3.0,
            tipoExpansion = TipoExpansion.EEV,
        )
        assertTrue(DiagnosticoRefrigeracion.evaluar(medicion).texto.contains("carga baja"))
    }

    @Test
    fun `la etiqueta del rango se formatea sin decimales innecesarios`() {
        assertEquals("8–12 °C", RangoObjetivo(8.0, 12.0).etiqueta())
        assertEquals("5.5–11 °C", RangoObjetivo(5.5, 11.0).etiqueta())
    }
}
