package com.checkingcontainer.core.domain

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val state: Flow<AuthState>
    suspend fun login(nick: String, pin: String): Result<Unit>
    suspend fun logout()
}