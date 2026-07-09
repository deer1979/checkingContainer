package com.checkingcontainer.feature.units

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkingcontainer.core.domain.ClientsRepository
import com.checkingcontainer.core.model.Client
import com.checkingcontainer.feature.units.navigation.CLIENTE_FORM_ID_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ClientesListViewModel @Inject constructor(
    repo: ClientsRepository,
) : ViewModel() {
    val clients: StateFlow<List<Client>> = repo.observeActive().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
}

data class ClientFormUiState(
    val isLoading: Boolean = true,
    val client: Client = Client(razonSocial = ""),
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class ClientFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: ClientsRepository,
) : ViewModel() {

    private val clientId: Long = savedStateHandle[CLIENTE_FORM_ID_ARG] ?: -1L

    private val _state = MutableStateFlow(ClientFormUiState())
    val state: StateFlow<ClientFormUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = if (clientId > 0) repo.findById(clientId) else null
            _state.update {
                it.copy(isLoading = false, client = existing ?: Client(razonSocial = ""))
            }
        }
    }

    fun onClientChange(client: Client) {
        _state.update { it.copy(client = client, errorMessage = null) }
    }

    fun save() {
        val client = _state.value.client
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching { repo.save(client) }
                .onSuccess { _state.update { it.copy(isSaving = false, saved = true) } }
                .onFailure { e ->
                    _state.update { it.copy(isSaving = false, errorMessage = e.message ?: "Error al guardar") }
                }
        }
    }

    fun deactivate() {
        if (clientId <= 0) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            runCatching { repo.deactivate(clientId) }
                .onSuccess { _state.update { it.copy(isSaving = false, saved = true) } }
                .onFailure { e ->
                    _state.update { it.copy(isSaving = false, errorMessage = e.message ?: "Error") }
                }
        }
    }
}
