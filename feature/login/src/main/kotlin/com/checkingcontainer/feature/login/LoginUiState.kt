package com.checkingcontainer.feature.login

import androidx.compose.runtime.Immutable

@Immutable
data class LoginUiState(
    val nick: String = "",
    val pin: String = "",
    val pinVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = !isSubmitting && nick.isNotBlank() && pin.length == 6 && pin.all(Char::isDigit)
}