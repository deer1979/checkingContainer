package com.checkingcontainer.feature.units

import com.checkingcontainer.core.model.CampoFicha
import com.checkingcontainer.core.model.DamageItem
import com.checkingcontainer.core.model.MedicionSnapshot
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
    val mediciones: List<MedicionSnapshot> = emptyList(),
    /** Mano de obra única del trabajo (instalación de los repuestos). */
    val manoDeObraTotal: Double? = null,
    // Cliente del catálogo (referencia + snapshot congelado al asignar)
    val clientId: Long? = null,
    val clientIdNumber: String = "",
    val clientDireccion: String = "",
    val clientTelefono: String = "",
    val clientEmail: String = "",
    val isSavingClient: Boolean = false,
    // Sitio del trabajo (cliente final) + orden de trabajo del contratante
    val sitioClienteId: Long? = null,
    val sitioNombre: String = "",
    val ordenTrabajo: String = "",
    // Ficha técnica del equipo (solo lectura; viene de la placa escaneada)
    val fichaTecnica: List<CampoFicha> = emptyList(),
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
    /** El estimado está guardado en el teléfono pero la nube no lo tiene. */
    val pendienteDeSubir: Boolean = false,
    /** Otra persona guardó cambios mientras yo tenía esto abierto. */
    val hayCambiosDelCompanero: Boolean = false,
    /** Campos que ambos editamos distinto en el último guardado. */
    val camposEnConflicto: List<String> = emptyList(),
    // Bottom sheet activo
    val activeSheet: EstimadoSheet? = null,
) {
    /**
     * Datos del cliente que faltan para que el encabezado del PDF salga completo.
     * No bloquea nada: solo alimenta el aviso previo a generar el reporte.
     */
    val datosClienteFaltantes: List<String>
        get() = buildList {
            if (clientName.isBlank()) add("Nombre del cliente")
            if (clientIdNumber.isBlank()) add("RUC / Cédula")
            if (clientTelefono.isBlank()) add("Teléfono")
            if (clientEmail.isBlank()) add("Correo electrónico")
            if (clientDireccion.isBlank()) add("Dirección")
        }
}

sealed interface EstimadoSheet {
    data object AddDamage : EstimadoSheet
    data class EditDamage(val itemId: String) : EstimadoSheet
    data class RepairItem(val itemId: String) : EstimadoSheet
    data class EditValor(val itemId: String) : EstimadoSheet
    data object EditManoDeObra : EstimadoSheet
}

sealed interface EstimadoEvent {
    data class ClientNameChange(val value: String) : EstimadoEvent
    data class LocationChange(val value: String) : EstimadoEvent
    data class OrdenTrabajoChange(val value: String) : EstimadoEvent
    data object ClearSitio : EstimadoEvent
    data class ShowSheet(val sheet: EstimadoSheet) : EstimadoEvent
    data object DismissSheet : EstimadoEvent
    data class IvaToggle(val enabled: Boolean) : EstimadoEvent
    // Daño
    data class DamageDescriptionChange(val value: String) : EstimadoEvent
    data object ConfirmAddDamage : EstimadoEvent
    data class ConfirmEditDamage(val itemId: String) : EstimadoEvent
    data class RemoveDamageItem(val itemId: String) : EstimadoEvent
    data class RemoveDamagePhoto(val itemId: String, val url: String) : EstimadoEvent
    data class RemoveRepairPhoto(val itemId: String, val url: String) : EstimadoEvent
    // Mediciones BLE
    data class RemoveMedicion(val timestamp: Long) : EstimadoEvent
    // Reparación
    data class RepairActionChange(val itemId: String, val value: String) : EstimadoEvent
    data class ConfirmRepair(val itemId: String) : EstimadoEvent
    // Valores
    data class CantidadChange(val itemId: String, val value: String) : EstimadoEvent
    data class PrecioUnitarioChange(val itemId: String, val value: String) : EstimadoEvent
    data class ManoDeObraChange(val value: String) : EstimadoEvent
    data object ConfirmManoDeObra : EstimadoEvent
    data class NombreItemChange(val value: String) : EstimadoEvent
    data class ConfirmValor(val itemId: String) : EstimadoEvent
}
