package com.checkingcontainer.core.model

import java.util.UUID

data class DamageItem(
    val id: String = UUID.randomUUID().toString(),
    /**
     * Nombre corto del ítem tal como aparece en la tabla de valores: el repuesto
     * ("Contactor C12 · P/N 3100-4520") o el servicio ("Limpieza de condensador
     * + lavado"). Un servicio se cobra igual que una pieza — cantidad × valor —
     * porque pueden ser dos o tres equipos, y su valor ya incluye la labor.
     */
    val nombre: String = "",
    /** Qué estaba mal. Va en la columna ANTES, junto a [damagePhotos]. */
    val damageDescription: String = "",
    val damagePhotos: List<String> = emptyList(),
    /** Qué se hizo. Va en la columna DESPUÉS, junto a [repairPhotos]. */
    val repairAction: String = "",
    val repairPhotos: List<String> = emptyList(),
    val status: DamageItemStatus = DamageItemStatus.PENDIENTE,
    /** Unidades del ítem (2 contactores, 3 limpiezas…). */
    val cantidad: Int = 1,
    /** Precio por unidad; el total de la línea es [cantidad] × este valor. */
    val precioUnitario: Double? = null,
    /**
     * Campos del esquema anterior (costo por ítem). Se conservan solo para poder
     * leer estimados creados antes de la tabla de valores con cantidades; al
     * cargarlos se convierten con [migrarDesdeCostosPorItem]. No se escriben.
     */
    @Deprecated("Usar precioUnitario × cantidad") val laborCost: Double? = null,
    @Deprecated("La mano de obra ahora es única por estimado") val materialCost: Double? = null,
) {
    /** Total de la línea en la tabla de valores. */
    val totalLinea: Double get() = (precioUnitario ?: 0.0) * cantidad

    /** Etiqueta para el PDF y la lista cuando el ítem aún no tiene nombre. */
    fun nombreParaMostrar(indice: Int): String =
        nombre.ifBlank { damageDescription.lineSequence().firstOrNull()?.take(60).orEmpty() }
            .ifBlank { "Ítem ${indice + 1}" }
}

/**
 * Convierte los ítems del esquema viejo (mano de obra + material por ítem) al
 * nuevo (cantidad × precio unitario + una mano de obra por estimado).
 *
 * El material del ítem pasa a precio unitario con cantidad 1 y la suma de todas
 * las manos de obra va al total único, de modo que **el total del estimado no
 * cambia**. Solo se aplica cuando el estimado aún no tiene datos nuevos.
 */
fun migrarDesdeCostosPorItem(
    damages: List<DamageItem>,
    manoDeObraTotal: Double?,
): Pair<List<DamageItem>, Double?> {
    @Suppress("DEPRECATION")
    val yaMigrado = manoDeObraTotal != null ||
        damages.all { it.laborCost == null && it.materialCost == null }
    if (yaMigrado) return damages to manoDeObraTotal

    @Suppress("DEPRECATION")
    val labor = damages.sumOf { it.laborCost ?: 0.0 }.takeIf { it > 0.0 }

    @Suppress("DEPRECATION")
    val convertidos = damages.map { item ->
        item.copy(
            cantidad = 1,
            precioUnitario = item.precioUnitario ?: item.materialCost,
            laborCost = null,
            materialCost = null,
        )
    }
    return convertidos to labor
}

/** Máximo de fotos por grupo (daño / reparación) en cada ítem. */
const val MAX_FOTOS_POR_GRUPO = 6

enum class DamageItemStatus { PENDIENTE, REPARADO }

data class Estimado(
    val id: Long = 0,
    val inspectionId: Long,
    // Datos del contenedor (snapshot al crear)
    val containerNo: String,
    val manufacturer: String = "",
    val unitModel: String = "",
    val unitModelNo: String = "",
    val unitSerialNo: String = "",
    val yearOfBuilt: String = "",
    val unitType: String = "",
    // Datos del estimado
    val clientName: String = "",
    // Referencia + snapshot del cliente del catálogo (congelado al asignar)
    val clientId: Long? = null,
    val clientIdNumber: String = "",
    val clientDireccion: String = "",
    val clientTelefono: String = "",
    val clientEmail: String = "",
    // Sitio donde se ejecuta el trabajo (cliente final, opcional) + nº de
    // orden de trabajo/contrato asignado por el contratante.
    val sitioClienteId: Long? = null,
    val sitioNombre: String = "",
    val ordenTrabajo: String = "",
    val location: String = "",
    val technicianId: Long = 0,
    val technicianName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val approvedAt: Long? = null,
    val closedAt: Long? = null,
    val status: EstimadoStatus = EstimadoStatus.ABIERTO,
    // Ítems
    val damages: List<DamageItem> = emptyList(),
    // Mediciones BLE capturadas (presiones, SH/SC, corriente) — ver MedicionSnapshot
    val mediciones: List<MedicionSnapshot> = emptyList(),
    /**
     * Mano de obra de todo el trabajo, en una sola línea: la instalación de los
     * repuestos listados. Los servicios (limpiezas, lavados) NO se cuentan aquí
     * porque ya cobran su labor dentro de su propio valor unitario.
     */
    val manoDeObraTotal: Double? = null,
    /**
     * Observaciones y recomendaciones para el cliente: lo que se vio pero NO se
     * cobra en este trabajo ("el condensador está saturado, se recomienda
     * limpieza"). Va al final del documento, en los dos tipos.
     */
    val observaciones: String = "",
    // Configuración
    val hasIva: Boolean = false,
    val reportUrl: String? = null,
    /** Última modificación. Decide quién gana cuando local y nube difieren. */
    val updatedAt: Long = 0L,
    /**
     * Momento en que Firestore confirmó la subida. `null` o anterior a
     * [updatedAt] significa que este estimado **solo existe en el teléfono**.
     */
    val subidoEn: Long? = null,
) {
    /** Hay trabajo guardado en el teléfono que la nube todavía no tiene. */
    val pendienteDeSubir: Boolean
        get() = subidoEn == null || subidoEn < updatedAt

    /**
     * Cuánta información real contiene, para elegir entre copias duplicadas de
     * la misma inspección: gana la que más datos tiene, nunca la que quedó en
     * blanco por haberse creado sobre una base local vacía.
     */
    val riqueza: Int
        get() = listOf(
            clientName, clientIdNumber, clientDireccion, clientTelefono, clientEmail,
            sitioNombre, ordenTrabajo, location, technicianName, observaciones,
        ).count { it.isNotBlank() } +
            damages.size * 3 +
            mediciones.size * 2 +
            (if (manoDeObraTotal != null) 1 else 0)
}

/**
 * Elige la mejor copia entre varios estimados de la misma inspección.
 *
 * Un teléfono que abrió el estimado sin tenerlo en local llegó a crear un
 * duplicado en blanco y subirlo, tapando al bueno. Ante el empate de datos
 * manda la modificación más reciente.
 */
fun mejorCopia(candidatos: List<Estimado>): Estimado? =
    candidatos.maxWithOrNull(
        compareBy<Estimado> { it.riqueza }.thenBy { it.updatedAt }.thenBy { it.id },
    )

enum class EstimadoStatus { ABIERTO, CERRADO }
