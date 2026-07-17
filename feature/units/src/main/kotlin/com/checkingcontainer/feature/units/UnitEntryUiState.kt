package com.checkingcontainer.feature.units

import androidx.compose.runtime.Immutable
import com.checkingcontainer.core.model.Brand
import com.checkingcontainer.core.model.Inspection
import com.checkingcontainer.core.model.InspStatus
import com.checkingcontainer.core.model.PtiInstruction
import com.checkingcontainer.core.model.ReeferEquipment
import com.checkingcontainer.core.model.TipoEquipo

enum class ScannerMode { CONTAINER, DATA_PLATE }

data class DuplicateWarning(val time: String, val technicianName: String)

@Immutable
data class UnitEntryUiState(
    val inspectionId: Long? = null,
    val tipoEquipo: TipoEquipo = TipoEquipo.REEFER,
    val containerNo: String = "",
    val unitModelNo: String = "",
    val unitModel: String = "",
    val unitType: String = "",
    val manufacturer: String = "",
    val unitSerialNo: String = "",
    val yearOfBuilt: String = "",
    val status: InspStatus = InspStatus.INSP,
    val ptiInstruction: PtiInstruction? = null,
    val brand: Brand = Brand.CARRIER,
    val deployedAs: String? = null,
    val observations: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val savedSuccessfully: Boolean = false,
    val navigateToEstimado: Long? = null,
    val showOrientationPicker: Boolean = false,
    val showScanner: Boolean = false,
    val scannerMode: ScannerMode = ScannerMode.CONTAINER,
    val scannerInitialVertical: Boolean = false,
    val isLookingUpCatalog: Boolean = false,
    val showValidation: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val isDeleting: Boolean = false,
    val deletedSuccessfully: Boolean = false,
    val catalogError: String? = null,
    val duplicateWarning: DuplicateWarning? = null,
) {
    val esReefer: Boolean get() = tipoEquipo == TipoEquipo.REEFER

    // REEFER: ISO 6346 con dígito verificador. Otros equipos: código libre (≥3).
    val isContainerValid: Boolean
        get() = if (esReefer) Iso6346.isValid(containerNo) else containerNo.trim().length >= 3

    val showContainerError: Boolean
        get() = esReefer && containerNo.length >= 4 && !isContainerValid

    val showSaveFab: Boolean
        get() = isContainerValid &&
            unitModelNo.isNotBlank() &&
            unitSerialNo.isNotBlank() &&
            yearOfBuilt.isNotBlank() &&
            (!esReefer || ptiInstruction != null) &&
            (brand != Brand.STAR_COOL || deployedAs != null)

    val canSave: Boolean
        get() = showSaveFab && !isSaving

    val showUnitModelNoError: Boolean get() = (showValidation || isContainerValid) && unitModelNo.isBlank()
    val showUnitSerialNoError: Boolean get() = (showValidation || isContainerValid) && unitSerialNo.isBlank()
    val showYearOfBuiltError: Boolean get() = (showValidation || isContainerValid) && yearOfBuilt.isBlank()
    val showPtiError: Boolean get() = esReefer && (showValidation || isContainerValid) && ptiInstruction == null
}

sealed interface UnitEntryEvent {
    data class ContainerNoChange(val value: String) : UnitEntryEvent
    data class UnitModelNoChange(val value: String) : UnitEntryEvent
    data class UnitSerialNoChange(val value: String) : UnitEntryEvent
    data class YearOfBuiltChange(val value: String) : UnitEntryEvent
    data class StatusChange(val value: InspStatus) : UnitEntryEvent
    data class PtiInstructionChange(val value: PtiInstruction) : UnitEntryEvent
    data class DeployedAsChange(val value: String) : UnitEntryEvent
    data class ObservationsChange(val value: String) : UnitEntryEvent
    data object OpenOrientationPicker : UnitEntryEvent
    data object DismissOrientationPicker : UnitEntryEvent
    data class OpenScanner(val mode: ScannerMode, val isVertical: Boolean = false) : UnitEntryEvent
    data object CloseScanner : UnitEntryEvent
    data class OcrResult(val fields: Map<String, String>) : UnitEntryEvent
    data object ShowDeleteConfirm : UnitEntryEvent
    data object DismissDeleteConfirm : UnitEntryEvent
    data object TriggerManualLookup : UnitEntryEvent
    data object DismissDuplicateWarning : UnitEntryEvent
}

internal fun UnitEntryUiState.applyOcrFields(fields: Map<String, String>): UnitEntryUiState {
    var updated = copy(showScanner = false, showOrientationPicker = false, errorMessage = null)
    fields.forEach { (key, value) ->
        updated = when (key) {
            "Container No." -> updated.copy(containerNo = value.uppercase())
            "Unit Model" -> updated.copy(unitModelNo = value)
            "Unit Serial No." -> updated.copy(unitSerialNo = value.uppercase())
            "Year of Built" -> updated.copy(yearOfBuilt = value)
            // Claves del escaneo de placa genérico (equipos no-reefer)
            "Manufacturer" -> updated.copy(manufacturer = value)
            "Observaciones" -> updated.copy(
                observations = if (updated.observations.isBlank()) value
                else "${updated.observations}\n$value",
            )
            else -> updated
        }
    }
    return updated
}

fun UnitEntryUiState.toEquipment(): ReeferEquipment = ReeferEquipment(
    containerNo = containerNo.trim().uppercase(),
    tipoEquipo = tipoEquipo,
    manufacturer = manufacturer.ifBlank { brand.label },
    unitModel = unitModel.trim(),
    unitModelNo = unitModelNo.trim(),
    unitSerialNo = unitSerialNo.trim().uppercase(),
    yearOfBuilt = yearOfBuilt.trim(),
    brand = brand,
    unitType = unitType,
)

fun UnitEntryUiState.toInspection(
    technicianId: Long,
    technicianName: String,
    location: String,
): Inspection = Inspection(
    containerNo = containerNo.trim().uppercase(),
    status = status,
    ptiInstruction = ptiInstruction,
    deployedAs = deployedAs,
    technicianId = technicianId,
    technicianName = technicianName,
    location = location,
    observations = observations.trim(),
)
