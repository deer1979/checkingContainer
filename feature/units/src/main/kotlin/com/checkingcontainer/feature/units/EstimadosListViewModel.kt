package com.checkingcontainer.feature.units

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkingcontainer.core.domain.EstimadosRepository
import com.checkingcontainer.core.model.Estimado
import dagger.hilt.android.lifecycle.HiltViewModel
import com.checkingcontainer.core.domain.AuthRepository
import com.checkingcontainer.core.domain.AuthState
import com.checkingcontainer.core.domain.BootstrapRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    private val repo: EstimadosRepository,
    private val authRepository: AuthRepository,
    private val bootstrapRepository: BootstrapRepository,
) : ViewModel() {

    private val _refrescando = MutableStateFlow(false)
    val refrescando: StateFlow<Boolean> = _refrescando.asStateFlow()

    /**
     * Trae del servidor lo que hayan guardado los compañeros. Antes la lista solo
     * se sincronizaba al iniciar sesión, así que trabajando toda la mañana sin
     * cerrar sesión no aparecía nada nuevo.
     */
    fun refrescar() {
        if (_refrescando.value) return
        viewModelScope.launch {
            _refrescando.value = true
            try {
                repo.subirPendientes()
                (authRepository.state.first() as? AuthState.Authenticated)?.user?.let {
                    bootstrapRepository.syncRecentAsync(it)
                }
            } finally {
                _refrescando.value = false
            }
        }
    }

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
