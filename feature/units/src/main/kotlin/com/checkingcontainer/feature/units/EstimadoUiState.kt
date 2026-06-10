package com.checkingcontainer.feature.units

import com.checkingcontainer.core.model.EstimadoStatus

data class EstimadoUiState(
    val estimadoId: Long = 0,
    val inspectionId: Long = 0,
    val containerNo: String = "",
    val technicianId: Long = 0,
    val technicianName: String = "",
    val location: String = "",
    val createdAt: Long = 0,
    val clientName: String = "",
    val damageDescription: String = "",
    val damagePhotos: List<String> = emptyList(),
    val repairDescription: String = "",
    val repairPhotos: List<String> = emptyList(),
    val status: EstimadoStatus = EstimadoStatus.ABIERTO,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isUploadingPhoto: Boolean = false,
    val errorMessage: String? = null,
    val savedMessage: String? = null,
    val showRepairSection: Boolean = false,
)

sealed interface EstimadoEvent {
    data class ClientNameChange(val value: String) : EstimadoEvent
    data class DamageDescriptionChange(val value: String) : EstimadoEvent
    data class RepairDescriptionChange(val value: String) : EstimadoEvent
    data object ShowRepairSection : EstimadoEvent
    data object MarkAsReparado : EstimadoEvent
    data class RemoveDamagePhoto(val url: String) : EstimadoEvent
    data class RemoveRepairPhoto(val url: String) : EstimadoEvent
}
