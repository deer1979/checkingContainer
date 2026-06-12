package com.checkingcontainer.feature.units

import com.checkingcontainer.feature.units.TextRecognitionAnalyzer.Companion.correctContainerChars
import com.checkingcontainer.feature.units.TextRecognitionAnalyzer.Companion.majorityVote
import org.junit.Assert.assertEquals
import org.junit.Test

class TextRecognitionAnalyzerTest {

    // ── correctContainerChars ────────────────────────────────────────────────

    @Test
    fun `corrige digitos leidos en posiciones de letra`() {
        // 0→O, 1→I, 8→B, 5→S en las primeras 4 posiciones
        assertEquals("OSQU3054383", correctContainerChars("0SQU3054383"))
        assertEquals("CIQU3054383", correctContainerChars("C1QU3054383"))
        assertEquals("CSBU3054383", correctContainerChars("CS8U3054383"))
    }

    @Test
    fun `corrige letras leidas en posiciones de digito`() {
        // O→0, I→1, B→8, S→5, G→6, Z→2 a partir de la posición 4
        assertEquals("CSQU3054383", correctContainerChars("CSQU3O54383"))
        assertEquals("CSQU3054183", correctContainerChars("CSQU3054I83"))
        assertEquals("CSQU6054283", correctContainerChars("CSQUG054Z83"))
    }

    @Test
    fun `la cuarta letra se fuerza a U si no es identificador de categoria`() {
        assertEquals("CSQU3054383", correctContainerChars("CSQ03054383"))
        assertEquals("CSQU3054383", correctContainerChars("CSQA3054383"))
    }

    @Test
    fun `respeta J y Z como identificadores de categoria validos`() {
        assertEquals("CSQJ3054383", correctContainerChars("CSQJ3054383"))
        assertEquals("CSQZ3054383", correctContainerChars("CSQZ3054383"))
    }

    @Test
    fun `entradas de longitud distinta a 11 se devuelven sin tocar`() {
        assertEquals("CSQU", correctContainerChars("CSQU"))
        assertEquals("", correctContainerChars(""))
    }

    // ── majorityVote ─────────────────────────────────────────────────────────

    @Test
    fun `voto por posicion reconstruye la lectura correcta`() {
        // Ningún frame es perfecto, pero cada posición tiene mayoría correcta
        val reads = listOf(
            "CSQU3054383",
            "CSQU3054383",
            "C5QU3054383", // error en pos 1
            "CSQU3O54383", // error en pos 5
        )
        assertEquals("CSQU3054383", majorityVote(reads))
    }

    @Test
    fun `con una sola lectura devuelve esa lectura`() {
        assertEquals("CSQU3054383", majorityVote(listOf("CSQU3054383")))
    }
}
