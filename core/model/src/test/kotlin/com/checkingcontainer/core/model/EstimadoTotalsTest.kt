package com.checkingcontainer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EstimadoTotalsTest {

    private fun item(cantidad: Int, precio: Double?, nombre: String = "pieza") = DamageItem(
        id = "x-$nombre-$cantidad",
        nombre = nombre,
        cantidad = cantidad,
        precioUnitario = precio,
    )

    @Test
    fun `cada linea multiplica cantidad por valor unitario`() {
        val totals = EstimadoTotals.calcular(
            listOf(item(2, 25.0, "contactor"), item(1, 32.0, "filtro")),
            hasIva = false,
        )
        assertEquals(82.0, totals.itemsTotal, 0.001)
        assertEquals(82.0, totals.total, 0.001)
    }

    @Test
    fun `la mano de obra es una sola linea aparte de los items`() {
        val totals = EstimadoTotals.calcular(
            listOf(item(2, 25.0)),
            hasIva = false,
            manoDeObraTotal = 80.0,
        )
        assertEquals(50.0, totals.itemsTotal, 0.001)
        assertEquals(80.0, totals.laborTotal, 0.001)
        assertEquals(130.0, totals.subtotal, 0.001)
        assertEquals(130.0, totals.total, 0.001)
    }

    @Test
    fun `un servicio se cobra igual que una pieza`() {
        // "Limpieza de condensador" en dos equipos = 2 × 50.
        val totals = EstimadoTotals.calcular(
            listOf(item(2, 50.0, "Limpieza de condensador + lavado")),
            hasIva = false,
        )
        assertEquals(100.0, totals.itemsTotal, 0.001)
    }

    @Test
    fun `items sin valor asignado cuentan como cero`() {
        val totals = EstimadoTotals.calcular(
            listOf(item(1, null), item(3, 10.0)),
            hasIva = false,
        )
        assertEquals(30.0, totals.total, 0.001)
    }

    @Test
    fun `IVA es 15 por ciento y solo se aplica si esta activo`() {
        val items = listOf(item(1, 100.0))
        assertEquals(0.15, EstimadoTotals.IVA_RATE, 0.0001)
        assertEquals("IVA 15%", EstimadoTotals.ivaLabel)

        val conIva = EstimadoTotals.calcular(items, hasIva = true)
        assertEquals(15.0, conIva.ivaAmount, 0.001)
        assertEquals(115.0, conIva.total, 0.001)

        // Es opcional: hay clientes que no lo pagan.
        val sinIva = EstimadoTotals.calcular(items, hasIva = false)
        assertEquals(0.0, sinIva.ivaAmount, 0.001)
        assertEquals(100.0, sinIva.total, 0.001)
    }

    @Test
    fun `el IVA grava tambien la mano de obra`() {
        val totals = EstimadoTotals.calcular(
            listOf(item(1, 100.0)),
            hasIva = true,
            manoDeObraTotal = 100.0,
        )
        assertEquals(200.0, totals.subtotal, 0.001)
        assertEquals(30.0, totals.ivaAmount, 0.001)
        assertEquals(230.0, totals.total, 0.001)
    }

    @Test
    fun `lista vacia da todo en cero`() {
        val totals = EstimadoTotals.calcular(emptyList(), hasIva = true)
        assertEquals(0.0, totals.total, 0.001)
        assertEquals(0.0, totals.ivaAmount, 0.001)
    }
}

/**
 * La conversión desde el esquema viejo (costo por ítem) no puede alterar lo que
 * ya se le cotizó a un cliente: el total tiene que salir idéntico.
 */
class MigracionCostosPorItemTest {

    @Suppress("DEPRECATION")
    private fun viejo(labor: Double?, material: Double?) = DamageItem(
        id = "viejo-$labor-$material",
        damageDescription = "daño",
        laborCost = labor,
        materialCost = material,
    )

    @Test
    fun `el material pasa a valor unitario y la mano de obra se agrupa`() {
        val (items, mano) = migrarDesdeCostosPorItem(
            listOf(viejo(100.0, 50.0), viejo(200.0, 25.5)),
            manoDeObraTotal = null,
        )

        assertEquals(300.0, mano!!, 0.001)
        assertEquals(50.0, items[0].precioUnitario!!, 0.001)
        assertEquals(25.5, items[1].precioUnitario!!, 0.001)
        assertEquals(1, items[0].cantidad)
    }

    @Test
    fun `el total del estimado no cambia al migrar`() {
        val originales = listOf(viejo(100.0, 50.0), viejo(200.0, 25.5))
        @Suppress("DEPRECATION")
        val totalViejo = originales.sumOf { (it.laborCost ?: 0.0) + (it.materialCost ?: 0.0) }

        val (items, mano) = migrarDesdeCostosPorItem(originales, null)
        val totalNuevo = EstimadoTotals.calcular(items, hasIva = false, manoDeObraTotal = mano).total

        assertEquals(totalViejo, totalNuevo, 0.001)
    }

    @Test
    fun `un estimado ya migrado no se vuelve a tocar`() {
        val nuevos = listOf(DamageItem(id = "a", nombre = "pieza", cantidad = 2, precioUnitario = 25.0))
        val (items, mano) = migrarDesdeCostosPorItem(nuevos, manoDeObraTotal = 80.0)
        assertEquals(nuevos, items)
        assertEquals(80.0, mano!!, 0.001)
    }

    @Test
    fun `sin costos viejos no se inventa mano de obra`() {
        val (_, mano) = migrarDesdeCostosPorItem(listOf(DamageItem(id = "a")), manoDeObraTotal = null)
        assertNull(mano)
    }

    @Test
    fun `el nombre a mostrar cae a la descripcion y luego al numero`() {
        val conNombre = DamageItem(id = "a", nombre = "Contactor C12")
        val soloDescripcion = DamageItem(id = "b", damageDescription = "Contactos carbonizados\nsegunda línea")
        val vacio = DamageItem(id = "c")

        assertEquals("Contactor C12", conNombre.nombreParaMostrar(0))
        assertEquals("Contactos carbonizados", soloDescripcion.nombreParaMostrar(1))
        assertEquals("Ítem 3", vacio.nombreParaMostrar(2))
    }
}
