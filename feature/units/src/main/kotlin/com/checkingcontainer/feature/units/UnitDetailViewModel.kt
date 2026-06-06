package com.checkingcontainer.feature.units

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkingcontainer.core.domain.InspectionRepository
import com.checkingcontainer.core.domain.ReeferEquipmentRepository
import com.checkingcontainer.core.model.Inspection
import com.checkingcontainer.core.model.ReeferEquipment
import com.checkingcontainer.feature.units.navigation.UNIT_DETAIL_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UnitDetailUiState(
    val equipment: ReeferEquipment? = null,
    val recentInspections: List<Inspection> = emptyList(),
    val remaining: Int = 0,
    val allInspections: List<Inspection> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingAll: Boolean = false,
    val hasLoadedAll: Boolean = false,
    val showLoadAllConfirm: Boolean = false,
)

@HiltViewModel
class UnitDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val equipmentRepo: ReeferEquipmentRepository,
    private val inspectionRepo: InspectionRepository,
) : ViewModel() {

    private val containerNo: String = checkNotNull(savedStateHandle[UNIT_DETAIL_ARG])

    private val _state = MutableStateFlow(UnitDetailUiState())
    val state: StateFlow<UnitDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val equipment = equipmentRepo.findByContainerNo(containerNo)
                ?: equipmentRepo.fetchFromFirestore(containerNo)
            val recent = inspectionRepo.getLatest2ByContainerNo(containerNo)
            val total = inspectionRepo.countByContainerNo(containerNo)
            _state.update {
                it.copy(
                    equipment = equipment,
                    recentInspections = recent,
                    remaining = maxOf(0, total - recent.size),
                    isLoading = false,
                )
            }
        }
    }

    fun requestLoadAll() {
        if (_state.value.hasLoadedAll || _state.value.isLoadingAll) return
        _state.update { it.copy(showLoadAllConfirm = true) }
    }

    fun dismissLoadAll() {
        _state.update { it.copy(showLoadAllConfirm = false) }
    }

    fun confirmLoadAll() {
        _state.update { it.copy(showLoadAllConfirm = false, isLoadingAll = true) }
        viewModelScope.launch {
            val all = inspectionRepo.getAllByContainerNo(containerNo)
            _state.update { it.copy(allInspections = all, isLoadingAll = false, hasLoadedAll = true) }
        }
    }
}
