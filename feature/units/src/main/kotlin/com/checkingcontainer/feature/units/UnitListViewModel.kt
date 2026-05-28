package com.checkingcontainer.feature.units

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkingcontainer.core.domain.ReeferUnitRepository
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
    private val repository: ReeferUnitRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(UnitListUiState())
    val state: StateFlow<UnitListUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeLast24h().collect { units ->
                _state.update { UnitListUiState(units = units.toImmutableList(), isLoading = false) }
            }
        }
    }

    fun onDelete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}
