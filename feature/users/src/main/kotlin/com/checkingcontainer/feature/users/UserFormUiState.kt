package com.checkingcontainer.feature.users

import com.checkingcontainer.core.model.JobTitle
import com.checkingcontainer.core.model.User
import com.checkingcontainer.core.model.UserRole
import com.checkingcontainer.core.model.generateNick

data class UserFormUiState(
    val id: Long? = null,
    val firstName: String = "",
    val lastName: String = "",
    val pin: String = "",
    val pinVisible: Boolean = false,
    val jobTitle: JobTitle = JobTitle.Tecnico,
    val role: UserRole = UserRole.Viewer,
    val company: String = "",
    val location: String = "",
    val isActive: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val savedSuccessfully: Boolean = false,
) {
    val previewNick: String
        get() = if (firstName.isBlank() || lastName.isBlank()) "" else generateNick(firstName, lastName)

    val canSave: Boolean
        get() = !isSaving &&
            firstName.isNotBlank() &&
            lastName.isNotBlank() &&
            company.isNotBlank() &&
            location.isNotBlank() &&
            pin.length == 6 && pin.all(Char::isDigit)
}

fun UserFormUiState.toDomain(): User = User(
    id = id ?: 0L,
    firstName = firstName.trim(),
    lastName = lastName.trim(),
    nick = previewNick,
    pin = pin,
    jobTitle = jobTitle,
    role = role,
    company = company.trim(),
    location = location.trim(),
    isActive = isActive,
)

fun User.toFormState(): UserFormUiState = UserFormUiState(
    id = id,
    firstName = firstName,
    lastName = lastName,
    pin = pin,
    jobTitle = jobTitle,
    role = role,
    company = company,
    location = location,
    isActive = isActive,
)