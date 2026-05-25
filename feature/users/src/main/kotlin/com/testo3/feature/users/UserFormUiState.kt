package com.testo3.feature.users

import com.testo3.core.model.JobTitle
import com.testo3.core.model.User
import com.testo3.core.model.UserRole
import com.testo3.core.model.buildEmail

/**
 * Shape of the create/edit form. [previewEmail] is derived in real time from
 * the first and last name fields and surfaced to the UI as read-only.
 */
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
    val previewEmail: String
        get() = if (firstName.isBlank() || lastName.isBlank()) "" else buildEmail(firstName, lastName)

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
    email = previewEmail,
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
