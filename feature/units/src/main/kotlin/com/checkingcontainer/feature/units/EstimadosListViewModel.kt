package com.checkingcontainer.feature.units

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkingcontainer.core.domain.EstimadosRepository
import com.checkingcontainer.core.model.Estimado
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class EstimadosListUiState(
    val openList: List<Estimado> = emptyList(),
    val closedList: List<Estimado> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class EstimadosListViewModel @Inject constructor(
    repo: EstimadosRepository,
) : ViewModel() {

    val state: StateFlow<EstimadosListUiState> = combine(
        repo.observeOpen(),
        repo.observeClosed(),
    ) { open, closed ->
        EstimadosListUiState(openList = open, closedList = closed, isLoading = false)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        EstimadosListUiState(),
    )
}
