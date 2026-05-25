package com.testo3.core.domain

import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for the current authentication state. Login is
 * against the local users table (email + PIN). Feature modules subscribe
 * to [state] and never store auth info on their own.
 */
interface AuthRepository {
    val state: Flow<AuthState>
    suspend fun login(email: String, pin: String): Result<Unit>
    suspend fun logout()
}
