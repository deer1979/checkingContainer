package com.checkingcontainer.feature.users

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkingcontainer.core.domain.UsersRepository
import com.checkingcontainer.core.model.JobTitle
import com.checkingcontainer.core.model.UserRole
import com.checkingcontainer.feature.users.navigation.USER_ID_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class UserFormViewModel @Inject constructor(
    private val repository: UsersRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val editingId: Long? = savedStateHandle.get<Long>(USER_ID_ARG)?.takeIf { it >= 0 }

    private val _state = MutableStateFlow(UserFormUiState())
    val state: StateFlow<UserFormUiState> = _state.asStateFlow()

    init {
        if (editingId != null) {
            viewModelScope.launch {
                repository.getById(editingId)?.let { user ->
                    _state.value = user.toFormState()
                }
            }
        }
    }

    fun onFirstNameChange(value: String) =
        _state.update { it.copy(firstName = value, errorMessage = null) }

    fun onLastNameChange(value: String) =
        _state.update { it.copy(lastName = value, errorMessage = null) }

    fun onPinChange(value: String) {
        val cleaned = value.filter(Char::isDigit).take(6)
        _state.update { it.copy(pin = cleaned, errorMessage = null) }
    }

    fun onTogglePinVisibility() =
        _state.update { it.copy(pinVisible = !it.pinVisible) }

    fun onCompanyChange(value: String) =
        _state.update { it.copy(company = value, errorMessage = null) }

    fun onLocationChange(value: String) =
        _state.update { it.copy(location = value, errorMessage = null) }

    fun onJobTitleChange(value: JobTitle) =
        _state.update { it.copy(jobTitle = value) }

    fun onRoleChange(value: UserRole) =
        _state.update { it.copy(role = value) }

    fun onToggleActive(value: Boolean) =
        _state.update { it.copy(isActive = value) }

    fun onSave() {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null) }
            val user = current.toDomain()
            val result = if (editingId == null) {
                repository.create(user).map { /* discard new id */ }
            } else {
                repository.update(user)
            }
            result.onSuccess {
                _state.update { it.copy(isSaving = false, savedSuccessfully = true) }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = error.message ?: "No se pudo guardar el usuario",
                    )
                }
            }
        }
    }
}
