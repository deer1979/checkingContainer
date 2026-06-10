package com.checkingcontainer.core.model

data class Estimado(
    val id: Long = 0,
    val inspectionId: Long,
    val containerNo: String,
    val clientName: String = "",
    val technicianId: Long = 0,
    val technicianName: String = "",
    val location: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
    val status: EstimadoStatus = EstimadoStatus.ABIERTO,
    val damageDescription: String = "",
    val damagePhotos: List<String> = emptyList(),
    val repairDescription: String = "",
    val repairPhotos: List<String> = emptyList(),
    val reportUrl: String? = null,
)

enum class EstimadoStatus { ABIERTO, REPARADO, CERRADO }

enum class EstimadoFase { DANO, REPARACION }
