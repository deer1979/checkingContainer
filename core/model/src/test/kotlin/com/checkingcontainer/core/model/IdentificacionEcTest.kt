package com.checkingcontainer.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentificacionEcTest {

    // Cédulas de ejemplo públicas con verificador correcto (módulo 10).
    @Test
    fun `cedula valida pasa`() {
        assertTrue(IdentificacionEc.cedulaValida("1710034065"))
        assertTrue(IdentificacionEc.cedulaValida("0926687856"))
    }

    @Test
    fun `cedula con verificador incorrecto falla`() {
        assertFalse(IdentificacionEc.cedulaValida("1710034066"))
        assertFalse(IdentificacionEc.cedulaValida("0926687857"))
    }

    @Test
    fun `cedula con formato invalido falla`() {
        assertFalse(IdentificacionEc.cedulaValida(""))
        assertFalse(IdentificacionEc.cedulaValida("123"))
        assertFalse(IdentificacionEc.cedulaValida("abcdefghij"))
        assertFalse(IdentificacionEc.cedulaValida("9910034065")) // provincia 99
        assertFalse(IdentificacionEc.cedulaValida("1770034065")) // tercer dígito 7
    }

    @Test
    fun `ruc persona natural = cedula valida + sufijo`() {
        assertTrue(IdentificacionEc.rucValido("1710034065001"))
        assertFalse(IdentificacionEc.rucValido("1710034065000")) // sufijo 000
        assertFalse(IdentificacionEc.rucValido("1710034066001")) // cédula inválida
    }

    @Test
    fun `ruc sociedad privada modulo 11`() {
        // 179000456 con coef 4-3-2-7-6-5-4-3-2 → verificador 2
        assertTrue(IdentificacionEc.rucValido("1790004562001"))
        assertFalse(IdentificacionEc.rucValido("1790004563001"))
    }

    @Test
    fun `ruc sector publico modulo 11`() {
        // 17600011 con coef 3-2-7-6-5-4-3-2 → verificador 2
        assertTrue(IdentificacionEc.rucValido("1760001120001"))
        assertFalse(IdentificacionEc.rucValido("1760001130001"))
    }

    @Test
    fun `ruc con formato invalido falla`() {
        assertFalse(IdentificacionEc.rucValido(""))
        assertFalse(IdentificacionEc.rucValido("1710034065"))    // 10 dígitos
        assertFalse(IdentificacionEc.rucValido("17100340650012")) // 14 dígitos
    }

    @Test
    fun `valida despacha por tipo`() {
        assertTrue(IdentificacionEc.valida(ClientIdType.CEDULA, "1710034065"))
        assertTrue(IdentificacionEc.valida(ClientIdType.RUC, "1710034065001"))
        assertTrue(IdentificacionEc.valida(ClientIdType.PASAPORTE, "AB123456"))
        assertFalse(IdentificacionEc.valida(ClientIdType.PASAPORTE, "  "))
    }
}
