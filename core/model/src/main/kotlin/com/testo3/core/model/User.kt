package com.testo3.core.model

/** Roles supported by the app. Drives where the user lands after login. */
enum class UserRole {
    Normal,
    Admin,
}

data class User(
    val id: String,
    val displayName: String,
    val email: String,
    val role: UserRole,
)
