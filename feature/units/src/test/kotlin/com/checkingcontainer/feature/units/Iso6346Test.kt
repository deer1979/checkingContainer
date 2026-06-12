package com.checkingcontainer.feature.units

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Iso6346Test {

    @Test
    fun `numero canonico ISO 6346 es valido`() {
        // Ejemplo oficial de la norma: CSQU 305438 con dígito verificador 3
        assertTrue(Iso6346.isValid("CSQU3054383"))
    }

    @Test
    fun `acepta minusculas y espacios alrededor`() {
        assertTrue(Iso6346.isValid(" csqu3054383 "))
    }

    @Test
    fun `rechaza digito verificador incorrecto`() {
        assertFalse(Iso6346.isValid("CSQU3054380"))
        assertFalse(Iso6346.isValid("CSQU3054384"))
    }

    @Test
    fun `rechaza longitud distinta de 11`() {
        assertFalse(Iso6346.isValid("CSQU305438"))
        assertFalse(Iso6346.isValid("CSQU30543831"))
        assertFalse(Iso6346.isValid(""))
    }

    @Test
    fun `rechaza identificador de categoria distinto de U J Z`() {
        // A=10 y U=32 comparten residuo mod 11: sin la restricción de categoría
        // el dígito verificador validaría esta lectura errónea del OCR.
        assertFalse(Iso6346.isValid("CSQA3054383"))
    }

    @Test
    fun `rechaza letras en posiciones de digitos`() {
        assertFalse(Iso6346.isValid("CSQU3O54383"))
    }

    @Test
    fun `computeCheckDigit coincide con el ejemplo canonico`() {
        assertEquals(3, Iso6346.computeCheckDigit("CSQU305438"))
    }

    @Test
    fun `computeCheckDigit devuelve -1 con entrada invalida`() {
        assertEquals(-1, Iso6346.computeCheckDigit("CSQU30543")) // 9 chars
        assertEquals(-1, Iso6346.computeCheckDigit("CSQ#305438"))
    }

    @Test
    fun `isValid acepta cualquier prefijo con su digito computado`() {
        for (owner in listOf("ABCU", "MSKU", "TRLU", "XXXU")) {
            val first10 = owner + "123456"
            val digit = Iso6346.computeCheckDigit(first10)
            assertTrue("$first10$digit debería ser válido", Iso6346.isValid("$first10$digit"))
        }
    }
}
