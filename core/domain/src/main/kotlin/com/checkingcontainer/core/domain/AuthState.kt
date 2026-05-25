package com.checkingcontainer.core.domain

import com.checkingcontainer.core.model.User

/**
 * Drives which top-level navigation graph is shown. The App shell switches
 * between public (splash + login) and authenticated (Scaffold with bottom
 * NavigationBar) on changes to this state.
 */
sealed interface AuthState {
    data object Loading : AuthState
    data object Unauthenticated : AuthState
    data class Authenticated(val user: User) : AuthState
}
