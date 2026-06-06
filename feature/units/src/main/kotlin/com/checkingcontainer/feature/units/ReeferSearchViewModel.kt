package com.checkingcontainer.feature.units

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkingcontainer.core.domain.InspectionRepository
import com.checkingcontainer.core.domain.ReeferEquipmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ReeferSearchViewModel @Inject constructor(
    private val equipmentRepo: ReeferEquipmentRepository,
    private val inspectionRepo: InspectionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReeferSearchUiState())
    val state: StateFlow<ReeferSearchUiState> = _state.asStateFlow()

    fun onQueryChange(query: String) {
        // ISO 6346 = 4 letras + 7 dígitos (11 chars). Filtra espacios y símbolos,
        // normaliza a mayúsculas y limita la longitud.
        val cleaned = query.filter { it.isLetterOrDigit() }.uppercase().take(11)
        _state.update { it.copy(query = cleaned, result = SearchResult.Idle) }
    }

    fun search() {
        val containerNo = _state.value.query.trim()
        if (!Iso6346.isValid(containerNo)) return

        viewModelScope.launch {
            _state.update { it.copy(isSearching = true, result = SearchResult.Idle) }

            // 1. Búsqueda local en Room — gratis
            var equipment = equipmentRepo.findByContainerNo(containerNo)

            // 2. Fallback a Firestore si no está en Room — 1 lectura
            if (equipment == null) {
                equipment = equipmentRepo.fetchFromFirestore(containerNo)
            }

            if (equipment == null) {
                _state.update { it.copy(isSearching = false, result = SearchResult.NotFound) }
                return@launch
            }

            val count = inspectionRepo.countByContainerNo(containerNo)
            _state.update {
                it.copy(
                    isSearching = false,
                    result = SearchResult.Found(equipment = equipment, inspectionCount = count),
                )
            }
        }
    }
}
