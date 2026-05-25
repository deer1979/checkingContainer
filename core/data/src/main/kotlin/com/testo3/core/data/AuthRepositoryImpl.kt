package com.testo3.core.data

import com.testo3.core.common.di.AppDispatcher
import com.testo3.core.common.di.Dispatcher
import com.testo3.core.domain.AuthRepository
import com.testo3.core.domain.AuthState
import com.testo3.core.model.User
import com.testo3.core.model.UserRole
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Placeholder implementation. Hard-coded rules:
 *   - Username containing "admin" → Admin role
 *   - Any other non-empty username with password length ≥ 4 → Normal role
 *
 * Swap to a real backend (Firebase Auth, Supabase, Auth0, OAuth) by writing
 * a different binding for [AuthRepository] in DataModule.
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : AuthRepository {

    private val _state = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val state = _state.asStateFlow()

    override suspend fun login(
        username: String,
        password: String,
    ): Result<Unit> = withContext(ioDispatcher) {
        delay(800)  // simulated round-trip
        if (username.isBlank() || password.length < 4) {
            return@withContext Result.failure(
                IllegalArgumentException("Credenciales inválidas")
            )
        }
        val role = if (username.contains("admin", ignoreCase = true)) {
            UserRole.Admin
        } else {
            UserRole.Normal
        }
        _state.value = AuthState.Authenticated(
            User(
                id = username.lowercase(),
                displayName = username.substringBefore('@').replaceFirstChar { it.uppercase() },
                email = if ('@' in username) username else "$username@testo3.local",
                role = role,
            )
        )
        Result.success(Unit)
    }

    override suspend fun logout() {
        _state.value = AuthState.Unauthenticated
    }
}
