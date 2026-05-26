package com.checkingcontainer.feature.units

import com.checkingcontainer.core.model.InspStatus
import com.checkingcontainer.core.model.PtiInstruction
import com.checkingcontainer.core.model.ReeferUnit
import com.checkingcontainer.core.model.UnitType

enum class ScannerMode { CONTAINER, DATA_PLATE }

data class UnitEntryUiState(
    val containerNo: String = "",
    val unitModel: String = "",
    val unitSerialNo: String = "",
    val yearOfBuilt: String = "",
    val status: InspStatus = InspStatus.INSP,
    val ptiInstruction: PtiInstruction? = null,
    val unitType: UnitType = UnitType.CARRIER,
    val deployedAs: String? = null,
    val observations: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val savedSuccessfully: Boolean = false,
    val showScanner: Boolean = false,
    val scannerMode: ScannerMode = ScannerMode.CONTAINER,
    val isLookingUpCatalog: Boolean = false,
) {
    val isContainerValid: Boolean get() = Iso6346.isValid(containerNo)

    val showContainerError: Boolean get() = containerNo.length >= 4 && !isContainerValid

    val canSave: Boolean
        get() = !isSaving &&
            isContainerValid &&
            unitModel.isNotBlank() &&
            unitSerialNo.isNotBlank() &&
            yearOfBuilt.isNotBlank() &&
            ptiInstruction != null &&
            (unitType != UnitType.STAR_COOL || deployedAs != null)
}

sealed interface UnitEntryEvent {
    data class ContainerNoChange(val value: String) : UnitEntryEvent
    data class UnitModelChange(val value: String) : UnitEntryEvent
    data class UnitSerialNoChange(val value: String) : UnitEntryEvent
    data class YearOfBuiltChange(val value: String) : UnitEntryEvent
    data class StatusChange(val value: InspStatus) : UnitEntryEvent
    data class PtiInstructionChange(val value: PtiInstruction) : UnitEntryEvent
    data class DeployedAsChange(val value: String) : UnitEntryEvent
    data class ObservationsChange(val value: String) : UnitEntryEvent
    data class OpenScanner(val mode: ScannerMode) : UnitEntryEvent
    data object CloseScanner : UnitEntryEvent
    data class OcrResult(val fields: Map<String, String>) : UnitEntryEvent
}

fun UnitEntryUiState.toDomain(technicianId: Long, technicianName: String): ReeferUnit = ReeferUnit(
    containerNo = containerNo.trim().uppercase(),
    manufacturer = unitType.label,
    unitModel = unitModel.trim(),
    unitSerialNo = unitSerialNo.trim().uppercase(),
    yearOfBuilt = yearOfBuilt.trim(),
    status = status,
    ptiInstruction = ptiInstruction,
    unitType = unitType,
    deployedAs = deployedAs,
    technicianId = technicianId,
    technicianName = technicianName,
    observations = observations.trim(),
)
