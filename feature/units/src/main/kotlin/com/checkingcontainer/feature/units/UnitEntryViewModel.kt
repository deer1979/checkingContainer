package com.checkingcontainer.feature.units

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkingcontainer.core.domain.AuthRepository
import com.checkingcontainer.core.domain.AuthState
import com.checkingcontainer.core.domain.ReeferUnitRepository
import com.checkingcontainer.core.domain.usecase.CatalogLookupUseCase
import com.checkingcontainer.core.model.Brand
import com.checkingcontainer.feature.units.navigation.UNIT_ENTRY_ID_ARG
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    savedStateHandle: SavedStateHandle,
    private val repository: ReeferUnitRepository,
    private val authRepository: AuthRepository,
    private val catalogLookupUseCase: CatalogLookupUseCase,
) : ViewModel() {

    private val editId: Long? = savedStateHandle.get<Long>(UNIT_ENTRY_ID_ARG)?.takeIf { it != -1L }

    private val _state = MutableStateFlow(UnitEntryUiState())
    val state: StateFlow<UnitEntryUiState> = _state.asStateFlow()

    // Evita re-disparar el lookup automático para el mismo modelo
    private var lastAutoTriggeredModel = ""

    init {
        editId?.let { loadUnit(it) }
    }

    private fun loadUnit(id: Long) {
        viewModelScope.launch {
            val unit = repository.getById(id) ?: return@launch
            _state.update {
                it.copy(
                    unitId = id,
                    containerNo = unit.containerNo,
                    unitModelNo = unit.unitModelNo,
                    unitModel = unit.unitModel,
                    unitType = unit.unitType,
                    manufacturer = unit.manufacturer,
                    unitSerialNo = unit.unitSerialNo,
                    yearOfBuilt = unit.yearOfBuilt,
                    status = unit.status,
                    ptiInstruction = unit.ptiInstruction,
                    brand = unit.brand,
                    deployedAs = unit.deployedAs,
                    observations = unit.observations,
                )
            }
        }
    }

    fun onEvent(event: UnitEntryEvent) {
        if (event == UnitEntryEvent.TriggerManualLookup) {
            val modelNo = _state.value.unitModelNo
            if (modelNo.isNotBlank()) triggerCatalogLookup(modelNo)
            return
        }
        _state.update { s ->
            when (event) {
                is UnitEntryEvent.ContainerNoChange ->
                    s.copy(containerNo = event.value.uppercase(), errorMessage = null, duplicateWarning = null)
                is UnitEntryEvent.UnitModelNoChange ->
                    s.copy(unitModelNo = event.value, errorMessage = null, catalogError = null)
                is UnitEntryEvent.UnitSerialNoChange ->
                    s.copy(unitSerialNo = event.value.uppercase(), errorMessage = null)
                is UnitEntryEvent.YearOfBuiltChange ->
                    s.copy(yearOfBuilt = event.value, errorMessage = null)
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
                UnitEntryEvent.ShowDeleteConfirm ->
                    s.copy(showDeleteConfirm = true)
                UnitEntryEvent.DismissDeleteConfirm ->
                    s.copy(showDeleteConfirm = false)
                UnitEntryEvent.TriggerManualLookup -> s  // handled before this block
                UnitEntryEvent.DismissDuplicateWarning ->
                    s.copy(duplicateWarning = null)
            }
        }
        if (event is UnitEntryEvent.ContainerNoChange && editId == null) {
            val containerNo = event.value.uppercase()
            if (Iso6346.isValid(containerNo)) checkDuplicate(containerNo)
        }
        if (event is UnitEntryEvent.UnitModelNoChange) {
            val model = event.value
            if (isCompleteCarrierModel(model) && model != lastAutoTriggeredModel) {
                lastAutoTriggeredModel = model
                triggerCatalogLookup(model)
            }
        }
    }

    private fun isCompleteCarrierModel(model: String): Boolean {
        val parts = model.split("-")
        if (parts.size != 3) return false
        val prefix = parts[0].uppercase()
        return (prefix == "69NT40" || prefix == "69NT20") &&
            parts[1].length == 3 && parts[1].all(Char::isDigit) &&
            parts[2].length == 3 && parts[2].all(Char::isDigit)
    }

    fun deleteUnit() {
        val id = _state.value.unitId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isDeleting = true, showDeleteConfirm = false) }
            repository.delete(id)
            _state.update { it.copy(isDeleting = false, deletedSuccessfully = true) }
        }
    }

    fun saveUnit() {
        val current = _state.value
        if (!current.canSave) {
            _state.update { it.copy(showValidation = true) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null) }
            val authUser = authRepository.state
                .filterIsInstance<AuthState.Authenticated>()
                .first()
                .user
            val domain = current.toDomain(authUser.id, authUser.fullName)
            val result = if (current.unitId != null) {
                repository.update(domain.copy(id = current.unitId))
            } else {
                repository.create(domain).map {}
            }
            result
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
                "Unit Model" -> updated.copy(unitModelNo = value)
                "Unit Serial No." -> updated.copy(unitSerialNo = value.uppercase())
                "Year of Built" -> updated.copy(yearOfBuilt = value)
                else -> updated
            }
        }
        val modelNo = updated.unitModelNo
        if (fields.containsKey("Unit Model") && modelNo.isNotBlank()) {
            triggerCatalogLookup(modelNo)
        }
        return updated
    }

    private fun checkDuplicate(containerNo: String) {
        viewModelScope.launch {
            val existing = repository.findTodayByContainerNo(containerNo) ?: return@launch
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(existing.createdAt))
            _state.update { it.copy(duplicateWarning = DuplicateWarning(timeStr, existing.technicianName)) }
        }
    }

    private fun triggerCatalogLookup(unitModelNo: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLookingUpCatalog = true, catalogError = null) }
            val lookup = runCatching { catalogLookupUseCase(unitModelNo) }
            _state.update { current ->
                lookup.fold(
                    onSuccess = { result ->
                        current.copy(
                            isLookingUpCatalog = false,
                            brand = result.brand,
                            manufacturer = result.manufacturer,
                            unitModel = result.unitModel,
                            unitType = result.unitType,
                            deployedAs = if (result.brand == Brand.STAR_COOL) result.deployedAs else null,
                            catalogError = null,
                        )
                    },
                    onFailure = { ex ->
                        current.copy(
                            isLookingUpCatalog = false,
                            catalogError = ex.message,
                        )
                    },
                )
            }
        }
    }
}
