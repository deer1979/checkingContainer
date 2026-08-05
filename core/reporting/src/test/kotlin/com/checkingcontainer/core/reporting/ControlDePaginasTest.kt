package com.checkingcontainer.core.reporting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Este test existe por un fallo que llegó a producción: el documento guardaba
 * todas las hojas abiertas para numerarlas al final, y `PdfDocument` reventaba
 * con **"Current page not finished!"** en cuanto el reporte pasaba de una página.
 * Ningún estimado podía generar PDF.
 *
 * `PdfDocument` es nativo y no corre en un test de JVM (Robolectric tampoco lo
 * simula), así que aquí se usa un doble con **su misma regla**: abrir una hoja
 * con otra sin cerrar es un error.
 */
class ControlDePaginasTest {

    /** Imita a `PdfDocument`: solo admite una hoja abierta a la vez. */
    private class DocumentoFalso {
        val abiertas = mutableListOf<Int>()
        val cerradas = mutableListOf<Int>()
        private var hayAbierta = false

        fun abrir(numero: Int) {
            if (hayAbierta) error("Current page not finished!")
            hayAbierta = true
            abiertas += numero
        }

        fun cerrar(numero: Int) {
            if (!hayAbierta) error("No page to finish")
            hayAbierta = false
            cerradas += numero
        }
    }

    private fun control(doc: DocumentoFalso) =
        ControlDePaginas(alAbrir = doc::abrir, alCerrar = doc::cerrar)

    @Test
    fun `cada hoja se cierra antes de abrir la siguiente`() {
        val doc = DocumentoFalso()
        val control = control(doc)

        control.iniciar()
        repeat(4) { control.siguiente() }
        val total = control.terminar()

        assertEquals(5, total)
        assertEquals(listOf(1, 2, 3, 4, 5), doc.abiertas)
        assertEquals(listOf(1, 2, 3, 4, 5), doc.cerradas)
    }

    @Test
    fun `un documento de una sola hoja tambien se cierra`() {
        val doc = DocumentoFalso()
        val control = control(doc)

        control.iniciar()
        val total = control.terminar()

        assertEquals(1, total)
        assertEquals(listOf(1), doc.cerradas)
    }

    @Test
    fun `la numeracion es correlativa desde uno`() {
        val doc = DocumentoFalso()
        val control = control(doc)

        control.iniciar()
        assertEquals(1, control.numero)
        control.siguiente()
        assertEquals(2, control.numero)
        control.siguiente()
        assertEquals(3, control.numero)
        control.terminar()
    }

    @Test
    fun `no deja abrir dos veces la primera hoja`() {
        val control = control(DocumentoFalso())
        control.iniciar()

        val error = assertThrows(IllegalStateException::class.java) { control.iniciar() }
        assertTrue(error.message!!.contains("abierta"))
    }

    @Test
    fun `no deja cerrar si no hay hoja abierta`() {
        val control = control(DocumentoFalso())
        assertThrows(IllegalStateException::class.java) { control.terminar() }
    }

    @Test
    fun `el doble reproduce el fallo original si se salta el cierre`() {
        // Comprobación de que el doble sirve: sin cerrar, abrir revienta igual
        // que PdfDocument. Es la garantía de que este test detectaría el bug.
        val doc = DocumentoFalso()
        doc.abrir(1)

        val error = assertThrows(IllegalStateException::class.java) { doc.abrir(2) }
        assertEquals("Current page not finished!", error.message)
    }
}
