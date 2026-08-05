package com.checkingcontainer.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextoBusquedaTest {

    @Test
    fun `encuentra sin escribir tildes`() {
        assertTrue("Compañía Álvarez".coincideCon("compania alvarez"))
        assertTrue("Compañía Álvarez".coincideCon("ALVAREZ"))
        assertTrue("Marítima Guayaquil".coincideCon("maritima"))
    }

    @Test
    fun `encuentra aunque el texto no tenga tildes y la consulta si`() {
        assertTrue("Maritima Guayaquil".coincideCon("Marítima"))
    }

    @Test
    fun `sigue distinguiendo lo que de verdad no coincide`() {
        assertFalse("Compañía Álvarez".coincideCon("Naviera"))
    }

    @Test
    fun `una consulta vacia coincide con todo`() {
        assertTrue("Cualquiera".coincideCon(""))
    }

    @Test
    fun `normaliza espacios sobrantes`() {
        assertTrue("Naviera X".coincideCon("  naviera  "))
    }
}
