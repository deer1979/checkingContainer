package com.checkingcontainer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reglas que protegen el trabajo del técnico cuando la nube y el teléfono no
 * están de acuerdo. Salieron de un caso real: un equipo recién instalado abrió
 * un estimado que no tenía en local, lo dio por nuevo y subió una copia en
 * blanco que tapó a la buena.
 */
class SincronizacionEstimadoTest {

    private fun estimado(
        id: Long,
        updatedAt: Long = 0L,
        subidoEn: Long? = null,
        cliente: String = "",
        damages: Int = 0,
    ) = Estimado(
        id = id,
        inspectionId = 1L,
        containerNo = "MSCU1234567",
        clientName = cliente,
        updatedAt = updatedAt,
        subidoEn = subidoEn,
        damages = List(damages) { DamageItem(id = "d$it", nombre = "pieza $it") },
    )

    // ── Pendiente de subir ──────────────────────────────────────────────────

    @Test
    fun `sin confirmacion de la nube queda pendiente`() {
        assertTrue(estimado(1, updatedAt = 100, subidoEn = null).pendienteDeSubir)
    }

    @Test
    fun `guardado despues de la ultima subida vuelve a quedar pendiente`() {
        // Se subió a las 100 y se siguió editando a las 200: la nube está vieja.
        assertTrue(estimado(1, updatedAt = 200, subidoEn = 100).pendienteDeSubir)
    }

    @Test
    fun `subido despues del ultimo cambio no esta pendiente`() {
        assertFalse(estimado(1, updatedAt = 100, subidoEn = 100).pendienteDeSubir)
        assertFalse(estimado(1, updatedAt = 100, subidoEn = 150).pendienteDeSubir)
    }

    // ── Elección entre copias duplicadas ────────────────────────────────────

    @Test
    fun `entre un duplicado en blanco y uno con datos gana el que tiene datos`() {
        val enBlanco = estimado(id = 9, updatedAt = 500)          // creado después
        val bueno = estimado(id = 2, updatedAt = 100, cliente = "Naviera X", damages = 3)

        // Aunque el vacío sea MÁS RECIENTE, no puede ganar: no aporta nada.
        assertEquals(bueno, mejorCopia(listOf(enBlanco, bueno)))
    }

    @Test
    fun `con la misma informacion gana la modificacion mas reciente`() {
        val vieja = estimado(id = 1, updatedAt = 100, cliente = "Naviera X", damages = 2)
        val nueva = estimado(id = 2, updatedAt = 900, cliente = "Naviera X", damages = 2)
        assertEquals(nueva, mejorCopia(listOf(vieja, nueva)))
    }

    @Test
    fun `una sola copia se devuelve tal cual y una lista vacia da null`() {
        val unica = estimado(id = 1, cliente = "Naviera X")
        assertEquals(unica, mejorCopia(listOf(unica)))
        assertNull(mejorCopia(emptyList()))
    }

    @Test
    fun `la riqueza cuenta datos del cliente e items`() {
        assertEquals(0, estimado(1).riqueza)
        assertEquals(1, estimado(1, cliente = "Naviera X").riqueza)
        // 1 del cliente + 3 por cada ítem
        assertEquals(7, estimado(1, cliente = "Naviera X", damages = 2).riqueza)
    }

    @Test
    fun `un estimado recien creado en blanco tiene riqueza cero`() {
        // Es exactamente la firma del duplicado que hay que descartar.
        assertEquals(0, Estimado(id = 5, inspectionId = 1, containerNo = "MSCU1234567").riqueza)
    }
}
