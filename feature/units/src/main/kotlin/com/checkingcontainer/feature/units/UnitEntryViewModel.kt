package com.checkingcontainer.feature.units

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkingcontainer.core.domain.AuthRepository
import com.checkingcontainer.core.domain.AuthState
import com.checkingcontainer.core.domain.ReeferUnitRepository
import com.checkingcontainer.core.domain.usecase.CatalogLookupUseCase
import com.checkingcontainer.core.model.UnitType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class UnitEntryViewModel @Inject constructor(
    private val repository: ReeferUnitRepository,
    private val authRepository: AuthRepository,
    private val catalogLookupUseCase: CatalogLookupUseCase,
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
                is UnitEntryEvent.UnitTypeChange ->
                    s.copy(unitType = event.value, deployedAs = null)
                is UnitEntryEvent.StatusChange ->
                    s.copy(status = event.value)
                is UnitEntryEvent.PtiInstructionChange ->
                    s.copy(ptiInstruction = event.value)
                is UnitEntryEvent.DeployedAsChange ->
                    s.copy(deployedAs = event.value)
                is UnitEntryEvent.ObservationsChange ->
                    s.copy(observations = event.value)
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
            val authUser = authRepository.state
                .filterIsInstance<AuthState.Authenticated>()
                .first()
                .user
            repository.create(current.toDomain(authUser.id, authUser.fullName))
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
        // Trigger catalog lookup when data plate fields arrive
        val model = updated.unitModel
        val serial = updated.unitSerialNo
        if (fields.containsKey("Unit Model") && model.isNotBlank()) {
            viewModelScope.launch {
                _state.update { it.copy(isLookingUpCatalog = true) }
                val result = runCatching { catalogLookupUseCase(model, serial) }.getOrNull()
                _state.update { current ->
                    if (result != null) {
                        current.copy(
                            isLookingUpCatalog = false,
                            unitType = result.unitType,
                            deployedAs = if (result.unitType == UnitType.STAR_COOL)
                                result.deployedAs else null,
                        )
                    } else {
                        current.copy(isLookingUpCatalog = false)
                    }
                }
            }
        }
        return updated
    }
}
