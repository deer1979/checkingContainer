package com.checkingcontainer.feature.units

import com.checkingcontainer.core.model.ReeferUnit

enum class ScannerMode { CONTAINER, DATA_PLATE }

data class UnitEntryUiState(
    val containerNo: String = "",
    val unitModel: String = "",
    val unitSerialNo: String = "",
    val yearOfBuilt: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val savedSuccessfully: Boolean = false,
    val showScanner: Boolean = false,
    val scannerMode: ScannerMode = ScannerMode.CONTAINER,
) {
    val canSave: Boolean
        get() = !isSaving &&
            containerNo.isNotBlank() &&
            unitModel.isNotBlank() &&
            unitSerialNo.isNotBlank() &&
            yearOfBuilt.isNotBlank()
}

sealed interface UnitEntryEvent {
    data class ContainerNoChange(val value: String) : UnitEntryEvent
    data class UnitModelChange(val value: String) : UnitEntryEvent
    data class UnitSerialNoChange(val value: String) : UnitEntryEvent
    data class YearOfBuiltChange(val value: String) : UnitEntryEvent
    data class OpenScanner(val mode: ScannerMode) : UnitEntryEvent
    data object CloseScanner : UnitEntryEvent
    data class OcrResult(val fields: Map<String, String>) : UnitEntryEvent
}

fun UnitEntryUiState.toDomain(): ReeferUnit = ReeferUnit(
    containerNo = containerNo.trim().uppercase(),
    unitModel = unitModel.trim(),
    unitSerialNo = unitSerialNo.trim().uppercase(),
    yearOfBuilt = yearOfBuilt.trim(),
)