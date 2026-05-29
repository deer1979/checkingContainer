package com.checkingcontainer.feature.units

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkingcontainer.core.domain.ReeferUnitRepository
import com.checkingcontainer.core.model.ReeferUnit
import com.checkingcontainer.feature.units.navigation.UNIT_DETAIL_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UnitDetailUiState(
    val latest: ReeferUnit? = null,
    val history: List<ReeferUnit> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingAll: Boolean = false,
    val hasLoadedAll: Boolean = false,
)

@HiltViewModel
class UnitDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ReeferUnitRepository,
) : ViewModel() {

    private val containerNo: String = checkNotNull(savedStateHandle[UNIT_DETAIL_ARG])

    private val _state = MutableStateFlow(UnitDetailUiState())
    val state: StateFlow<UnitDetailUiState> = _state.asStateFlow()

    init {
        repository.observeLatestByContainerNo(containerNo)
            .onEach { latest -> _state.update { it.copy(latest = latest, isLoading = false) } }
            .launchIn(viewModelScope)
    }

    fun loadAll() {
        if (_state.value.hasLoadedAll || _state.value.isLoadingAll) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingAll = true) }
            val all = repository.getAllByContainerNo(containerNo)
            _state.update { it.copy(history = all, isLoadingAll = false, hasLoadedAll = true) }
        }
    }
}
