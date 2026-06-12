package com.checkingcontainer.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EstimadoTotalsTest {

    private fun item(labor: Double?, material: Double?) = DamageItem(
        id = "x",
        damageDescription = "test",
        laborCost = labor,
        materialCost = material,
    )

    @Test
    fun `suma mano de obra y materiales por separado`() {
        val totals = EstimadoTotals.calcular(
            listOf(item(100.0, 50.0), item(200.0, 25.5)),
            hasIva = false,
        )
        assertEquals(300.0, totals.laborTotal, 0.001)
        assertEquals(75.5, totals.materialTotal, 0.001)
        assertEquals(375.5, totals.subtotal, 0.001)
        assertEquals(0.0, totals.ivaAmount, 0.001)
        assertEquals(375.5, totals.total, 0.001)
    }

    @Test
    fun `costos nulos cuentan como cero`() {
        val totals = EstimadoTotals.calcular(
            listOf(item(null, null), item(100.0, null)),
            hasIva = false,
        )
        assertEquals(100.0, totals.total, 0.001)
    }

    @Test
    fun `IVA es 12 por ciento del subtotal`() {
        val totals = EstimadoTotals.calcular(listOf(item(100.0, 0.0)), hasIva = true)
        assertEquals(12.0, totals.ivaAmount, 0.001)
        assertEquals(112.0, totals.total, 0.001)
    }

    @Test
    fun `lista vacia da todo en cero`() {
        val totals = EstimadoTotals.calcular(emptyList(), hasIva = true)
        assertEquals(0.0, totals.total, 0.001)
        assertEquals(0.0, totals.ivaAmount, 0.001)
    }
}
