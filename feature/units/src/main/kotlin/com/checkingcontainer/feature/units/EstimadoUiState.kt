package com.checkingcontainer.feature.units

import com.checkingcontainer.core.model.DamageItem
import com.checkingcontainer.core.model.EstimadoStatus

data class EstimadoUiState(
    val estimadoId: Long = 0,
    val inspectionId: Long = 0,
    // Datos del contenedor
    val containerNo: String = "",
    val manufacturer: String = "",
    val unitModel: String = "",
    val unitModelNo: String = "",
    val unitSerialNo: String = "",
    val yearOfBuilt: String = "",
    val unitType: String = "",
    // Datos del estimado
    val clientName: String = "",
    val location: String = "",
    val technicianName: String = "",
    val createdAt: Long = 0,
    val approvedAt: Long? = null,
    val status: EstimadoStatus = EstimadoStatus.ABIERTO,
    // Ítems
    val damages: List<DamageItem> = emptyList(),
    // Configuración
    val hasIva: Boolean = false,
    // Estados de carga
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    /** Hay modificaciones desde la última carga/guardado (aviso al salir). */
    val isDirty: Boolean = false,
    val isUploadingPhoto: Boolean = false,
    val isGeneratingPdf: Boolean = false,
    val errorMessage: String? = null,
    val savedMessage: String? = null,
    // PDF listo para previsualizar (ruta al archivo temporal)
    val pdfPreviewPath: String? = null,
    // Bottom sheet activo
    val activeSheet: EstimadoSheet? = null,
)

sealed interface EstimadoSheet {
    data object AddDamage : EstimadoSheet
    data class EditDamage(val itemId: String) : EstimadoSheet
    data class RepairItem(val itemId: String) : EstimadoSheet
    data class EditValor(val itemId: String) : EstimadoSheet
}

sealed interface EstimadoEvent {
    data class ClientNameChange(val value: String) : EstimadoEvent
    data class LocationChange(val value: String) : EstimadoEvent
    data class ShowSheet(val sheet: EstimadoSheet) : EstimadoEvent
    data object DismissSheet : EstimadoEvent
    data class IvaToggle(val enabled: Boolean) : EstimadoEvent
    // Daño
    data class DamageDescriptionChange(val value: String) : EstimadoEvent
    data object ConfirmAddDamage : EstimadoEvent
    data class ConfirmEditDamage(val itemId: String) : EstimadoEvent
    data class RemoveDamageItem(val itemId: String) : EstimadoEvent
    data class RemoveDamagePhoto(val itemId: String) : EstimadoEvent
    data class RemoveRepairPhoto(val itemId: String) : EstimadoEvent
    // Reparación
    data class RepairActionChange(val itemId: String, val value: String) : EstimadoEvent
    data class ConfirmRepair(val itemId: String) : EstimadoEvent
    // Valores
    data class LaborCostChange(val itemId: String, val value: String) : EstimadoEvent
    data class MaterialCostChange(val itemId: String, val value: String) : EstimadoEvent
    data class ConfirmValor(val itemId: String) : EstimadoEvent
}
