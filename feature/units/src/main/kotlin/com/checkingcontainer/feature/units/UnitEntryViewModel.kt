package com.checkingcontainer.feature.units

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkingcontainer.core.domain.ReeferUnitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class UnitEntryViewModel @Inject constructor(
    private val repository: ReeferUnitRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(UnitEntryUiState())
    val state: StateFlow<UnitEntryUiState> = _state.asStateFlow()

    fun onEvent(event: UnitEntryEvent) {
        _state.update { s ->
            when (event) {
                is UnitEntryEvent.ContainerNoChange ->
                    s.copy(containerNo = event.value.uppercase(), errorMessage = null)
                is UnitEntryEvent.UnitModelChange ->
                    s.copy(unitModel = event.value, errorMessage = null)
                is UnitEntryEvent.UnitSerialNoChange ->
                    s.copy(unitSerialNo = event.value.uppercase(), errorMessage = null)
                is UnitEntryEvent.YearOfBuiltChange ->
                    s.copy(yearOfBuilt = event.value, errorMessage = null)
                is UnitEntryEvent.OpenScanner ->
                    s.copy(showScanner = true, scannerMode = event.mode)
                UnitEntryEvent.CloseScanner ->
                    s.copy(showScanner = false)
                is UnitEntryEvent.OcrResult ->
                    applyOcrResult(s, event.fields)
            }
        }
    }

    fun saveUnit() {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null) }
            repository.create(current.toDomain())
                .onSuccess {
                    _state.update { it.copy(isSaving = false, savedSuccessfully = true) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.message ?: "No se pudo guardar la unidad",
                        )
                    }
                }
        }
    }

    private fun applyOcrResult(s: UnitEntryUiState, fields: Map<String, String>): UnitEntryUiState {
        var updated = s.copy(showScanner = false, errorMessage = null)
        fields.forEach { (key, value) ->
            updated = when (key) {
                "Container No." -> updated.copy(containerNo = value.uppercase())
                "Unit Model" -> updated.copy(unitModel = value)
                "Unit Serial No." -> updated.copy(unitSerialNo = value.uppercase())
                "Year of Built" -> updated.copy(yearOfBuilt = value)
                else -> updated
            }
        }
        return updated
    }
}