package com.checkingcontainer.core.model

enum class UserRole {
    SuperAdmin,
    Admin,
    Editor,
    Viewer,
    ;

    val isAdmin: Boolean get() = this == SuperAdmin || this == Admin
}

enum class JobTitle(val display: String) {
    Tecnico("Técnico"),
    Ayudante("Ayudante"),
    Digitador("Digitador"),
    Operador("Operador"),
    JefeDePatio("Jefe de Patio"),
    JefeDeDepartamento("Jefe de Departamento"),
    Lider("Líder"),
}

data class User(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val nick: String,
    val pin: String,
    val jobTitle: JobTitle,
    val role: UserRole,
    val company: String,
    val location: String,
    val isActive: Boolean,
) {
    val fullName: String get() = "$firstName $lastName".trim()
}

/** nick = first letter of firstName + lastName, lowercase, no spaces/specials. */
fun generateNick(firstName: String, lastName: String): String {
    val initial = firstName.firstOrNull()?.lowercaseChar()?.toString().orEmpty()
    val last = lastName.lowercase().filter { it.isLetterOrDigit() }
    return "$initial$last"
}