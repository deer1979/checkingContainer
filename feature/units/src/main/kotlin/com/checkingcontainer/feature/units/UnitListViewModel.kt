package com.checkingcontainer.feature.units

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkingcontainer.core.domain.InspectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class UnitListViewModel @Inject constructor(
    private val inspectionRepo: InspectionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(UnitListUiState())
    val state: StateFlow<UnitListUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            inspectionRepo.observeLast24h().collect { items ->
                _state.update { UnitListUiState(units = items.toImmutableList(), isLoading = false) }
            }
        }
    }

    fun onDelete(id: Long) {
        viewModelScope.launch { inspectionRepo.delete(id) }
    }
}
