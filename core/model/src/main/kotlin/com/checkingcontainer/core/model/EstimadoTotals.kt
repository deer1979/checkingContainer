package com.checkingcontainer.core.model

/**
 * Totales de un estimado. Única fuente de verdad del cálculo: la pantalla de
 * valores y el PDF usan esta misma función (antes cada uno lo duplicaba).
 */
data class EstimadoTotals(
    val laborTotal: Double,
    val materialTotal: Double,
    val subtotal: Double,
    val ivaAmount: Double,
    val total: Double,
) {
    companion object {
        const val IVA_RATE = 0.12

        fun calcular(damages: List<DamageItem>, hasIva: Boolean): EstimadoTotals {
            val labor = damages.sumOf { it.laborCost ?: 0.0 }
            val material = damages.sumOf { it.materialCost ?: 0.0 }
            val subtotal = labor + material
            val iva = if (hasIva) subtotal * IVA_RATE else 0.0
            return EstimadoTotals(
                laborTotal = labor,
                materialTotal = material,
                subtotal = subtotal,
                ivaAmount = iva,
                total = subtotal + iva,
            )
        }
    }
}
