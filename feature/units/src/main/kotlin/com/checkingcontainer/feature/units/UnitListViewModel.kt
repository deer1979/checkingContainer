package com.checkingcontainer.feature.units

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkingcontainer.core.domain.InspectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class UnitListViewModel @Inject constructor(
    private val inspectionRepo: InspectionRepository,
) : ViewModel() {

    val state: StateFlow<UnitListUiState> = inspectionRepo.observeLast24h()
        .map { items -> UnitListUiState(units = items.toImmutableList(), isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UnitListUiState())

    fun onDelete(id: Long) {
        viewModelScope.launch { inspectionRepo.delete(id) }
    }
}
