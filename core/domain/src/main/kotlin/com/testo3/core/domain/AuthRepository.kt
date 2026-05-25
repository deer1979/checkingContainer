package com.testo3.core.domain

import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for the current authentication state. The feature
 * modules subscribe to [state] and never store auth info on their own.
 *
 * Replace the in-memory implementation in :core:data with a real cloud
 * client when needed — no caller of this interface needs to change.
 */
interface AuthRepository {
    val state: Flow<AuthState>
    suspend fun login(username: String, password: String): Result<Unit>
    suspend fun logout()
}
