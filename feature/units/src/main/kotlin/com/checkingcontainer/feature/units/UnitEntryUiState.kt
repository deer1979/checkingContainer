package com.checkingcontainer.feature.units

import androidx.compose.runtime.Immutable
import com.checkingcontainer.core.model.Brand
import com.checkingcontainer.core.model.InspStatus
import com.checkingcontainer.core.model.PtiInstruction
import com.checkingcontainer.core.model.ReeferUnit

enum class ScannerMode { CONTAINER, DATA_PLATE }

data class DuplicateWarning(val time: String, val technicianName: String)

@Immutable
data class UnitEntryUiState(
    val unitId: Long? = null,
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
    val showScanner: Boolean = false,
    val scannerMode: ScannerMode = ScannerMode.CONTAINER,
    val isLookingUpCatalog: Boolean = false,
    val showValidation: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val isDeleting: Boolean = false,
    val deletedSuccessfully: Boolean = false,
    val catalogError: String? = null,
    val duplicateWarning: DuplicateWarning? = null,
) {
    val isContainerValid: Boolean get() = Iso6346.isValid(containerNo)

    val showContainerError: Boolean get() = containerNo.length >= 4 && !isContainerValid

    val canSave: Boolean
        get() = !isSaving &&
            isContainerValid &&
            unitModelNo.isNotBlank() &&
            unitSerialNo.isNotBlank() &&
            yearOfBuilt.isNotBlank() &&
            ptiInstruction != null &&
            (brand != Brand.STAR_COOL || deployedAs != null)

    val showUnitModelNoError: Boolean get() = showValidation && unitModelNo.isBlank()
    val showUnitSerialNoError: Boolean get() = showValidation && unitSerialNo.isBlank()
    val showYearOfBuiltError: Boolean get() = showValidation && yearOfBuilt.isBlank()
    val showPtiError: Boolean get() = showValidation && ptiInstruction == null
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
    data class OpenScanner(val mode: ScannerMode) : UnitEntryEvent
    data object CloseScanner : UnitEntryEvent
    data class OcrResult(val fields: Map<String, String>) : UnitEntryEvent
    data object ShowDeleteConfirm : UnitEntryEvent
    data object DismissDeleteConfirm : UnitEntryEvent
    data object TriggerManualLookup : UnitEntryEvent
    data object DismissDuplicateWarning : UnitEntryEvent
}

fun UnitEntryUiState.toDomain(technicianId: Long, technicianName: String): ReeferUnit = ReeferUnit(
    containerNo = containerNo.trim().uppercase(),
    manufacturer = manufacturer.ifBlank { brand.label },
    unitModel = unitModel.trim(),
    unitModelNo = unitModelNo.trim(),
    unitSerialNo = unitSerialNo.trim().uppercase(),
    yearOfBuilt = yearOfBuilt.trim(),
    status = status,
    ptiInstruction = ptiInstruction,
    brand = brand,
    unitType = unitType,
    deployedAs = deployedAs,
    technicianId = technicianId,
    technicianName = technicianName,
    observations = observations.trim(),
)
