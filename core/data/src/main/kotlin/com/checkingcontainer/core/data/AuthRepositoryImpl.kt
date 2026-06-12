package com.checkingcontainer.core.data

import com.checkingcontainer.core.common.di.AppDispatcher
import com.checkingcontainer.core.common.di.Dispatcher
import com.checkingcontainer.core.common.security.PinHasher
import com.checkingcontainer.core.database.dao.UserDao
import com.checkingcontainer.core.domain.AuthRepository
import com.checkingcontainer.core.domain.AuthState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val firestoreService: FirestoreService,
    @param:Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : AuthRepository {

    private val _state = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val state = _state.asStateFlow()

    override suspend fun login(
        nick: String,
        pin: String,
    ): Result<Unit> = withContext(ioDispatcher) {
        val normalizedNick = nick.trim().lowercase()
        val row = userDao.findByNick(normalizedNick)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Usuario no encontrado"),
            )
        if (!row.isActive) {
            return@withContext Result.failure(
                IllegalStateException("Cuenta desactivada — contacta a un administrador"),
            )
        }
        if (!PinHasher.verify(pin, row.pin)) {
            return@withContext Result.failure(
                IllegalArgumentException("PIN incorrecto"),
            )
        }
        // Migración perezosa: si el PIN guardado era texto plano legado y el
        // login fue correcto, se re-guarda hasheado (Room + nube).
        if (!PinHasher.isHashed(row.pin)) {
            val migrated = row.copy(pin = PinHasher.hash(pin))
            userDao.update(migrated)
            firestoreService.upsertUser(migrated)
        }
        _state.value = AuthState.Authenticated(row.toDomain())
        Result.success(Unit)
    }

    override suspend fun logout() {
        _state.value = AuthState.Unauthenticated
    }
}