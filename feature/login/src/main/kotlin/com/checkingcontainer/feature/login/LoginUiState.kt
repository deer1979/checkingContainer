package com.checkingcontainer.feature.login

data class LoginUiState(
    val email: String = "",
    val pin: String = "",
    val pinVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = !isSubmitting && email.contains("@") && pin.length == 6 && pin.all(Char::isDigit)
}
