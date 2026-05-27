package com.checkingcontainer.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkingcontainer.core.domain.AuthRepository
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

    fun onNickChange(value: String) {
        _state.update { it.copy(nick = value.trim().lowercase(), errorMessage = null) }
    }

    fun onPinChange(value: String) {
        val cleaned = value.filter(Char::isDigit).take(6)
        _state.update { it.copy(pin = cleaned, errorMessage = null) }
    }

    fun onTogglePinVisibility() {
        _state.update { it.copy(pinVisible = !it.pinVisible) }
    }

    fun onSubmit() {
        val current = _state.value
        if (!current.canSubmit) {
            _state.update { it.copy(showValidation = true) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, errorMessage = null) }
            authRepository.login(current.nick, current.pin)
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = error.message ?: "Error desconocido",
                        )
                    }
                }
                .onSuccess {
                    _state.value = LoginUiState()
                }
        }
    }
}