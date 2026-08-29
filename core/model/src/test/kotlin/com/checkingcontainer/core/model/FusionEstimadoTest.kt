package com.checkingcontainer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El caso real que hay que proteger: uno hace el borrador y guarda; el otro
 * abre, agrega una foto y pone valores, y guarda. Como tocaron cosas distintas,
 * **no se debe perder nada ni preguntar nada**.
 */
class FusionEstimadoTest {

    private fun base(
        items: List<DamageItem> = emptyList(),
        cliente: String = "",
        manoDeObra: Double? = null,
    ) = Estimado(
        id = 1L,
        inspectionId = 7L,
        containerNo = "MSCU1234567",
        clientName = cliente,
        manoDeObraTotal = manoDeObra,
        damages = items,
    )

    private fun item(
        id: String,
        nombre: String = "Contactor",
        dano: String = "quemado",
        precio: Double? = null,
        fotosDano: List<String> = emptyList(),
        reparacion: String = "",
        estado: DamageItemStatus = DamageItemStatus.PENDIENTE,
    ) = DamageItem(
        id = id,
        nombre = nombre,
        damageDescription = dano,
        precioUnitario = precio,
        damagePhotos = fotosDano,
        repairAction = reparacion,
        status = estado,
    )

    // ── El caso del compañero ───────────────────────────────────────────────

    @Test
    fun `el otro agrega foto y yo pongo valor, se juntan sin preguntar`() {
        val comun = base(items = listOf(item("a")))
        // Él le agregó una foto al ítem.
        val suyo = base(items = listOf(item("a", fotosDano = listOf("foto1.jpg"))))
        // Yo, sin ver su foto, le puse precio.
        val mio = base(items = listOf(item("a", precio = 25.0)))

        val r = fusionarEstimado(comun, mio, suyo)

        assertEquals(listOf("foto1.jpg"), r.estimado.damages[0].damagePhotos)
        assertEquals(25.0, r.estimado.damages[0].precioUnitario!!, 0.001)
        assertTrue(r.camposEnConflicto.isEmpty())
        assertTrue(r.huboCambiosDelOtro)
    }

    @Test
    fun `las fotos de los dos se unen, no compiten`() {
        val comun = base(items = listOf(item("a")))
        val suyo = base(items = listOf(item("a", fotosDano = listOf("suya.jpg"))))
        val mio = base(items = listOf(item("a", fotosDano = listOf("mia.jpg"))))

        val r = fusionarEstimado(comun, mio, suyo)

        assertEquals(listOf("mia.jpg", "suya.jpg"), r.estimado.damages[0].damagePhotos)
        assertTrue("Subir fotos distintas no es un conflicto", r.camposEnConflicto.isEmpty())
    }

    @Test
    fun `un item que creo el otro se incorpora`() {
        val comun = base(items = listOf(item("a")))
        val suyo = base(items = listOf(item("a"), item("b", nombre = "Filtro secador")))
        val mio = base(items = listOf(item("a", precio = 10.0)))

        val r = fusionarEstimado(comun, mio, suyo)

        assertEquals(2, r.estimado.damages.size)
        assertEquals("Filtro secador", r.estimado.damages[1].nombre)
    }

    @Test
    fun `campos distintos del encabezado se combinan`() {
        val comun = base()
        val suyo = base(cliente = "Naviera X")
        val mio = base(manoDeObra = 80.0)

        val r = fusionarEstimado(comun, mio, suyo)

        assertEquals("Naviera X", r.estimado.clientName)
        assertEquals(80.0, r.estimado.manoDeObraTotal!!, 0.001)
        assertTrue(r.camposEnConflicto.isEmpty())
    }

    // ── Conflictos de verdad ────────────────────────────────────────────────

    @Test
    fun `si los dos cambiamos el mismo campo se reporta y gana el mio`() {
        val comun = base(cliente = "Sin asignar")
        val suyo = base(cliente = "Naviera X")
        val mio = base(cliente = "Naviera Y")

        val r = fusionarEstimado(comun, mio, suyo)

        assertEquals("Naviera Y", r.estimado.clientName)
        assertEquals(listOf("Cliente"), r.camposEnConflicto)
    }

    @Test
    fun `dos precios distintos para el mismo item se reportan`() {
        val comun = base(items = listOf(item("a")))
        val suyo = base(items = listOf(item("a", precio = 30.0)))
        val mio = base(items = listOf(item("a", precio = 25.0)))

        val r = fusionarEstimado(comun, mio, suyo)

        assertEquals(25.0, r.estimado.damages[0].precioUnitario!!, 0.001)
        assertEquals(1, r.camposEnConflicto.size)
        assertTrue(r.camposEnConflicto.first().contains("valor"))
    }

    // ── Reglas particulares ─────────────────────────────────────────────────

    @Test
    fun `si alguno marco el item como reparado, queda reparado`() {
        val comun = base(items = listOf(item("a")))
        val suyo = base(items = listOf(item("a", reparacion = "cambiado", estado = DamageItemStatus.REPARADO)))
        val mio = base(items = listOf(item("a")))

        val r = fusionarEstimado(comun, mio, suyo)

        assertEquals(DamageItemStatus.REPARADO, r.estimado.damages[0].status)
        assertEquals("cambiado", r.estimado.damages[0].repairAction)
    }

    @Test
    fun `las mediciones de ambos se unen y quedan ordenadas`() {
        val comun = base()
        val suyo = comun.copy(mediciones = listOf(MedicionSnapshot(timestamp = 300)))
        val mio = comun.copy(
            mediciones = listOf(MedicionSnapshot(timestamp = 100), MedicionSnapshot(timestamp = 200)),
        )

        val r = fusionarEstimado(comun, mio, suyo)

        assertEquals(listOf(100L, 200L, 300L), r.estimado.mediciones.map { it.timestamp })
    }

    @Test
    fun `si el otro borro un item que yo no toque se respeta el borrado`() {
        val comun = base(items = listOf(item("a"), item("b")))
        val suyo = base(items = listOf(item("a")))
        val mio = base(items = listOf(item("a"), item("b")))

        val r = fusionarEstimado(comun, mio, suyo)

        assertEquals(1, r.estimado.damages.size)
        assertEquals("a", r.estimado.damages[0].id)
    }

    @Test
    fun `si yo edite el item que el borro, el mio sobrevive`() {
        // Perder trabajo recién escrito es peor que conservar un ítem de más.
        val comun = base(items = listOf(item("a"), item("b")))
        val suyo = base(items = listOf(item("a")))
        val mio = base(items = listOf(item("a"), item("b", precio = 40.0)))

        val r = fusionarEstimado(comun, mio, suyo)

        assertEquals(2, r.estimado.damages.size)
        assertEquals(40.0, r.estimado.damages[1].precioUnitario!!, 0.001)
    }

    @Test
    fun `sin cambios del otro no se reporta nada`() {
        val comun = base(items = listOf(item("a")), cliente = "Naviera X")
        val mio = comun.copy(manoDeObraTotal = 50.0)

        val r = fusionarEstimado(comun, mio, comun)

        assertFalse(r.huboCambiosDelOtro)
        assertTrue(r.camposEnConflicto.isEmpty())
        assertEquals(50.0, r.estimado.manoDeObraTotal!!, 0.001)
    }

    @Test
    fun `sin base se conserva lo mio y se suma lo que el agrego`() {
        // Puede pasar si la app se reinició: no se sabe quién cambió qué, así que
        // lo prudente es no borrar nada de ninguno de los dos.
        val suyo = base(items = listOf(item("a"), item("b", nombre = "Filtro")), cliente = "Naviera X")
        val mio = base(items = listOf(item("a", precio = 25.0)), cliente = "Naviera Y")

        val r = fusionarEstimado(null, mio, suyo)

        assertEquals("Naviera Y", r.estimado.clientName)
        assertEquals(2, r.estimado.damages.size)
        assertEquals(25.0, r.estimado.damages[0].precioUnitario!!, 0.001)
    }

    // ── Observaciones y cierre ──────────────────────────────────────────────

    @Test
    fun `las observaciones que escribio el otro no se pierden`() {
        val comun = base(items = listOf(item("a")))
        val suyo = comun.copy(observaciones = "El condensador está saturado, se recomienda limpieza")
        val mio = comun.copy(manoDeObraTotal = 40.0)

        val r = fusionarEstimado(comun, mio, suyo)

        assertEquals("El condensador está saturado, se recomienda limpieza", r.estimado.observaciones)
        assertTrue(r.camposEnConflicto.isEmpty())
    }

    @Test
    fun `las fotos de las observaciones de los dos se unen`() {
        val comun = base(items = listOf(item("a")))
        val suyo = comun.copy(observacionesFotos = listOf("condensador.jpg"))
        val mio = comun.copy(observacionesFotos = listOf("evaporador.jpg"))

        val r = fusionarEstimado(comun, mio, suyo)

        assertEquals(listOf("evaporador.jpg", "condensador.jpg"), r.estimado.observacionesFotos)
        assertTrue(r.camposEnConflicto.isEmpty())
    }

    @Test
    fun `si el otro cerro el estimado y yo no lo toque, queda cerrado`() {
        val comun = base(items = listOf(item("a")))
        val suyo = comun.copy(status = EstimadoStatus.CERRADO, closedAt = 1_700_000_000_000L)
        val mio = comun.copy(manoDeObraTotal = 40.0)

        val r = fusionarEstimado(comun, mio, suyo)

        assertEquals(EstimadoStatus.CERRADO, r.estimado.status)
        assertEquals(1_700_000_000_000L, r.estimado.closedAt)
    }

    @Test
    fun `si yo lo reabri, la fecha de cierre no queda colgando`() {
        val comun = base(items = listOf(item("a")))
            .copy(status = EstimadoStatus.CERRADO, closedAt = 1_700_000_000_000L)
        val mio = comun.copy(status = EstimadoStatus.ABIERTO, closedAt = null)

        val r = fusionarEstimado(comun, mio, comun)

        assertEquals(EstimadoStatus.ABIERTO, r.estimado.status)
        assertEquals(null, r.estimado.closedAt)
    }
}
