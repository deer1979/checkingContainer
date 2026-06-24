package com.checkingcontainer.feature.units

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkingcontainer.core.domain.EstimadosRepository
import com.checkingcontainer.core.model.Estimado
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContainerSearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<Estimado> = emptyList(),
    val searched: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ContainerSearchViewModel @Inject constructor(
    private val repo: EstimadosRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ContainerSearchUiState())
    val state: StateFlow<ContainerSearchUiState> = _state.asStateFlow()

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value.uppercase().trim(), error = null) }
    }

    fun onSearch() {
        val containerNo = _state.value.query.trim()
        if (containerNo.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isSearching = true, error = null, searched = false) }
            runCatching { repo.searchByContainerNo(containerNo) }
                .onSuccess { list ->
                    _state.update {
                        it.copy(
                            isSearching = false,
                            results = list,
                            searched = true,
                            error = if (list.isEmpty()) "No se encontraron estimados para «$containerNo»" else null,
                        )
                    }
                }
                .onFailure {
                    _state.update {
                        it.copy(
                            isSearching = false,
                            searched = true,
                            results = emptyList(),
                            error = "Sin conexión o contenedor no encontrado",
                        )
                    }
                }
        }
    }
}
