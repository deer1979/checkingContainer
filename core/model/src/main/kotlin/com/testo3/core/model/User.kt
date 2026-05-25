package com.testo3.core.model

enum class UserRole {
    SuperAdmin,
    Admin,
    Editor,
    Viewer,
    ;

    /** Can manage other users, publish announcements, see admin panel. */
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

/**
 * Identity + assignment record. Auth uses [email] + [pin]; the rest is for
 * audit (who works where, with what contractor, under whose role).
 */
data class User(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val email: String,
    val pin: String,
    val jobTitle: JobTitle,
    val role: UserRole,
    val company: String,
    val location: String,
    val isActive: Boolean,
) {
    val fullName: String get() = "$firstName $lastName".trim()
}

/**
 * Helper used by both the form ViewModel and the seed step in the database
 * callback. Email = first letter of first name + full last name (lowercased,
 * stripped) + "@tt3.com".
 */
fun buildEmail(firstName: String, lastName: String): String {
    val initial = firstName.firstOrNull()?.lowercase().orEmpty()
    val last = lastName.lowercase().filter { !it.isWhitespace() }
    return "$initial$last@tt3.com"
}
