package com.checkingcontainer.feature.units

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkingcontainer.core.domain.AuthRepository
import com.checkingcontainer.core.domain.AuthState
import com.checkingcontainer.core.domain.InspectionRepository
import com.checkingcontainer.core.domain.ReeferEquipmentRepository
import com.checkingcontainer.core.domain.usecase.CatalogLookupUseCase
import com.checkingcontainer.core.model.Brand
import com.checkingcontainer.core.model.InspStatus
import com.checkingcontainer.feature.units.navigation.UNIT_ENTRY_ID_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    private val equipmentRepo: ReeferEquipmentRepository,
    private val inspectionRepo: InspectionRepository,
    private val authRepository: AuthRepository,
    private val catalogLookupUseCase: CatalogLookupUseCase,
) : ViewModel() {

    private val editId: Long? = savedStateHandle.get<Long>(UNIT_ENTRY_ID_ARG)?.takeIf { it != -1L }

    private val _state = MutableStateFlow(UnitEntryUiState())
    val state: StateFlow<UnitEntryUiState> = _state.asStateFlow()

    private var lastAutoTriggeredModel = ""

    init {
        editId?.let { loadInspection(it) }
    }

    private fun loadInspection(id: Long) {
        viewModelScope.launch {
            val inspection = inspectionRepo.findById(id) ?: return@launch
            val equipment = equipmentRepo.findByContainerNo(inspection.containerNo)
            _state.update {
                it.copy(
                    inspectionId = id,
                    containerNo = inspection.containerNo,
                    unitModelNo = equipment?.unitModelNo ?: "",
                    unitModel = equipment?.unitModel ?: "",
                    unitType = equipment?.unitType ?: "",
                    manufacturer = equipment?.manufacturer ?: "",
                    unitSerialNo = equipment?.unitSerialNo ?: "",
                    yearOfBuilt = equipment?.yearOfBuilt ?: "",
                    brand = equipment?.brand ?: Brand.CARRIER,
                    status = inspection.status,
                    ptiInstruction = inspection.ptiInstruction,
                    deployedAs = inspection.deployedAs,
                    observations = inspection.observations,
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
                is UnitEntryEvent.StatusChange -> s.copy(status = event.value)
                is UnitEntryEvent.PtiInstructionChange -> s.copy(ptiInstruction = event.value)
                is UnitEntryEvent.DeployedAsChange -> s.copy(deployedAs = event.value)
                is UnitEntryEvent.ObservationsChange -> s.copy(observations = event.value)
                UnitEntryEvent.OpenOrientationPicker -> s.copy(showOrientationPicker = true)
                UnitEntryEvent.DismissOrientationPicker -> s.copy(showOrientationPicker = false)
                is UnitEntryEvent.OpenScanner -> s.copy(
                    showOrientationPicker = false,
                    showScanner = true,
                    scannerMode = event.mode,
                    scannerInitialVertical = event.isVertical,
                )
                UnitEntryEvent.CloseScanner -> s.copy(showScanner = false)
                is UnitEntryEvent.OcrResult -> s.applyOcrFields(event.fields)
                UnitEntryEvent.ShowDeleteConfirm -> s.copy(showDeleteConfirm = true)
                UnitEntryEvent.DismissDeleteConfirm -> s.copy(showDeleteConfirm = false)
                UnitEntryEvent.TriggerManualLookup -> s
                UnitEntryEvent.DismissDuplicateWarning -> s.copy(duplicateWarning = null)
            }
        }
        if (event is UnitEntryEvent.OcrResult) {
            val modelNo = _state.value.unitModelNo
            if (event.fields.containsKey("Unit Model") && modelNo.isNotBlank()) {
                triggerCatalogLookup(modelNo)
            }
        }
        if (event is UnitEntryEvent.ContainerNoChange && editId == null) {
            val containerNo = event.value.uppercase()
            if (Iso6346.isValid(containerNo)) checkDuplicate(containerNo)
        }
        if (event is UnitEntryEvent.UnitModelNoChange) {
            val model = event.value
            if (Iso6346.isCompleteCarrierModel(model) && model != lastAutoTriggeredModel) {
                lastAutoTriggeredModel = model
                triggerCatalogLookup(model)
            }
        }
    }

    fun deleteUnit() {
        val id = _state.value.inspectionId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isDeleting = true, showDeleteConfirm = false) }
            inspectionRepo.delete(id)
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

            val equipment = current.toEquipment()
            val inspection = current.toInspection(authUser.id, authUser.fullName, authUser.location)

            var savedInspectionId = current.inspectionId ?: 0L

            val result: Result<Unit> = if (current.inspectionId != null) {
                equipmentRepo.upsert(equipment)
                inspectionRepo.update(inspection.copy(id = current.inspectionId))
            } else {
                equipmentRepo.upsert(equipment)
                inspectionRepo.create(inspection).also { r ->
                    savedInspectionId = r.getOrElse { 0L }
                }.map {}
            }

            result
                .onSuccess {
                    val navTarget = if (current.status == InspStatus.EST && savedInspectionId != 0L) {
                        savedInspectionId
                    } else null
                    _state.update { it.copy(isSaving = false, savedSuccessfully = true, navigateToEstimado = navTarget) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.message ?: "No se pudo guardar la inspección",
                        )
                    }
                }
        }
    }

    private fun checkDuplicate(containerNo: String) {
        viewModelScope.launch {
            val existing = inspectionRepo.findTodayByContainerNo(containerNo) ?: return@launch
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
