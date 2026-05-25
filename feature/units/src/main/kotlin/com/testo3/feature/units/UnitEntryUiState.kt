package com.testo3.feature.units

import com.testo3.core.model.ReeferUnit

data class UnitEntryUiState(
    val containerNo: String = "",
    val unitModel: String = "",
    val unitSerialNo: String = "",
    val yearOfBuilt: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val savedSuccessfully: Boolean = false,
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
}

fun UnitEntryUiState.toDomain(): ReeferUnit = ReeferUnit(
    containerNo = containerNo.trim().uppercase(),
    unitModel = unitModel.trim(),
    unitSerialNo = unitSerialNo.trim().uppercase(),
    yearOfBuilt = yearOfBuilt.trim(),
)