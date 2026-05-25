package com.testo3.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.testo3.core.domain.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onUsernameChange(value: String) {
        _state.update { it.copy(username = value, errorMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _state.update { it.copy(password = value, errorMessage = null) }
    }

    fun onTogglePasswordVisibility() {
        _state.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun onSubmit() {
        val current = _state.value
        if (!current.canSubmit) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, errorMessage = null) }
            authRepository.login(current.username, current.password)
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = error.message ?: "Error desconocido",
                        )
                    }
                }
                .onSuccess {
                    // App shell observes AuthRepository.state and swaps to the
                    // authenticated graph automatically. We just clear the form.
                    _state.value = LoginUiState()
                }
        }
    }
}
