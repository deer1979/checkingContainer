package com.checkingcontainer.core.model

/**
 * Totales de un estimado. Única fuente de verdad del cálculo: la pantalla de
 * valores y el PDF usan esta misma función (antes cada uno lo duplicaba).
 *
 * Estructura de la tabla de valores:
 * ```
 *   Nº  Descripción              Cant.  V. Unit.     Total
 *   1   Contactor C12              2     $25.00     $50.00   ← cantidad × unitario
 *   2   Limpieza de condensador    2     $50.00    $100.00
 *                                   Subtotal ítems  $150.00
 *                                     Mano de obra   $80.00   ← una sola línea
 *                                         Subtotal  $230.00
 *                                          IVA 15%   $34.50   ← opcional
 *                                            TOTAL  $264.50
 * ```
 */
data class EstimadoTotals(
    /** Suma de cantidad × precio unitario de todos los ítems. */
    val itemsTotal: Double,
    /** Mano de obra de instalación, única para todo el trabajo. */
    val laborTotal: Double,
    val subtotal: Double,
    val ivaAmount: Double,
    val total: Double,
) {
    companion object {
        /**
         * IVA vigente en Ecuador (subió del 12% en abril de 2024). Es opcional:
         * hay clientes que no lo pagan y entonces la línea ni siquiera aparece.
         */
        const val IVA_RATE = 0.15

        /** Etiqueta con el porcentaje, para no escribirlo a mano en dos sitios. */
        val ivaLabel: String get() = "IVA ${(IVA_RATE * 100).toInt()}%"

        fun calcular(
            damages: List<DamageItem>,
            hasIva: Boolean,
            manoDeObraTotal: Double? = null,
        ): EstimadoTotals {
            val items = damages.sumOf { it.totalLinea }
            val labor = manoDeObraTotal ?: 0.0
            val subtotal = items + labor
            val iva = if (hasIva) subtotal * IVA_RATE else 0.0
            return EstimadoTotals(
                itemsTotal = items,
                laborTotal = labor,
                subtotal = subtotal,
                ivaAmount = iva,
                total = subtotal + iva,
            )
        }
    }
}
