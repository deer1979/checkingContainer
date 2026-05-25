package com.testo3.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class LoginViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onUsernameChange(value: String) {
        _state.update { it.copy(username = value) }
    }

    fun onPasswordChange(value: String) {
        _state.update { it.copy(password = value) }
    }

    fun onTogglePasswordVisibility() {
        _state.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    /**
     * Placeholder: simulates a 1s network round-trip then resets the form.
     * Wire this to a real AuthRepository when we add auth (Cloud / Supabase /
     * Firebase Auth / etc.) — the screen layer doesn't need to change.
     */
    fun onSubmit() {
        if (!_state.value.canSubmit) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            delay(1_000)
            _state.update { it.copy(isSubmitting = false, password = "") }
        }
    }
}
