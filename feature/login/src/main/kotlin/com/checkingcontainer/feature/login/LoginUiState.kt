package com.checkingcontainer.feature.login

import androidx.compose.runtime.Immutable

@Immutable
data class LoginUiState(
    val nick: String = "",
    val pin: String = "",
    val pinVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val showValidation: Boolean = false,
) {
    val pinIsValid: Boolean get() = pin.length == 6 && pin.all(Char::isDigit)
    val canSubmit: Boolean get() = !isSubmitting && nick.isNotBlank() && pinIsValid
    val showNickError: Boolean get() = showValidation && nick.isBlank()
    val showPinError: Boolean get() = showValidation && !pinIsValid
}