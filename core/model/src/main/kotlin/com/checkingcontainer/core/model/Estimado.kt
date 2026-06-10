package com.checkingcontainer.core.model

import java.util.UUID

data class DamageItem(
    val id: String = UUID.randomUUID().toString(),
    val damageDescription: String = "",
    val damagePhoto: String? = null,
    val repairAction: String = "",
    val repairPhoto: String? = null,
    val status: DamageItemStatus = DamageItemStatus.PENDIENTE,
    val laborCost: Double? = null,
    val materialCost: Double? = null,
)

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
    val location: String = "",
    val technicianId: Long = 0,
    val technicianName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val approvedAt: Long? = null,
    val closedAt: Long? = null,
    val status: EstimadoStatus = EstimadoStatus.ABIERTO,
    // Ítems
    val damages: List<DamageItem> = emptyList(),
    // Configuración
    val hasIva: Boolean = false,
    val reportUrl: String? = null,
)

enum class EstimadoStatus { ABIERTO, CERRADO }
