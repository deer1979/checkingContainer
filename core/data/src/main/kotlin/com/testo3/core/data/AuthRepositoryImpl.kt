package com.testo3.core.data

import com.testo3.core.common.di.AppDispatcher
import com.testo3.core.common.di.Dispatcher
import com.testo3.core.database.dao.UserDao
import com.testo3.core.domain.AuthRepository
import com.testo3.core.domain.AuthState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Authenticates against the local Room user table. The first SuperAdmin is
 * seeded by [com.testo3.core.database.di.DatabaseModule] so the user can log
 * in on a fresh install without a registration screen.
 *
 * Rules:
 *  - Email must match a row in `users`.
 *  - PIN must equal the stored 6-digit PIN exactly.
 *  - The matched user must have isActive = true.
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : AuthRepository {

    private val _state = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val state = _state.asStateFlow()

    override suspend fun login(
        email: String,
        pin: String,
    ): Result<Unit> = withContext(ioDispatcher) {
        val normalizedEmail = email.trim().lowercase()
        val row = userDao.findByEmail(normalizedEmail)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Usuario no encontrado")
            )
        if (!row.isActive) {
            return@withContext Result.failure(
                IllegalStateException("Cuenta desactivada — contacta a un administrador")
            )
        }
        if (row.pin != pin) {
            return@withContext Result.failure(
                IllegalArgumentException("PIN incorrecto")
            )
        }
        _state.value = AuthState.Authenticated(row.toDomain())
        Result.success(Unit)
    }

    override suspend fun logout() {
        _state.value = AuthState.Unauthenticated
    }
}
