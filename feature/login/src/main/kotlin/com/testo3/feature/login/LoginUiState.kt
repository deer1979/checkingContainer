package com.testo3.feature.login

/**
 * Pure UI state for the login screen. The screen reads only this; any change
 * has to come through the ViewModel so recomposition is predictable.
 */
data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val isSubmitting: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !isSubmitting && username.isNotBlank() && password.length >= 4
}
