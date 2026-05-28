package com.checkingcontainer.core.data

import android.content.Context
import android.util.Log
import com.checkingcontainer.core.common.di.AppDispatcher
import com.checkingcontainer.core.common.di.Dispatcher
import com.checkingcontainer.core.data.sync.RemoteSyncWorker
import com.checkingcontainer.core.database.dao.UserDao
import com.checkingcontainer.core.database.entity.UserEntity
import com.checkingcontainer.core.database.entity.toEntity
import com.checkingcontainer.core.domain.UsersRepository
import com.checkingcontainer.core.model.User
import com.checkingcontainer.core.network.RemoteDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repositorio de usuarios.
 *
 * ## Sincronización
 * Cada escritura (create / update / setActive) guarda en Room con `syncPending = true`
 * y encola [RemoteSyncWorker] para subir el cambio a Google Sheets.
 *
 * [UserDao.setActive] actualiza también `syncPending = 1` en el mismo SQL,
 * por lo que no necesita pasar por [toEntity].
 */
@Singleton
class UsersRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    @ApplicationContext private val context: Context,
    private val remoteDataSource: RemoteDataSource,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : UsersRepository {

    private val syncScope = CoroutineScope(ioDispatcher + SupervisorJob())

    // ── Reads ─────────────────────────────────────────────────────────────────

    override fun observeAll(): Flow<List<User>> =
        userDao.observeAll()
            .map { rows -> rows.map(UserEntity::toDomain) }
            .flowOn(ioDispatcher)

    override suspend fun getById(id: Long): User? = withContext(ioDispatcher) {
        userDao.findById(id)?.toDomain()
    }

    override suspend fun count(): Int = withContext(ioDispatcher) { userDao.count() }

    // ── Writes ────────────────────────────────────────────────────────────────

    override suspend fun create(user: User): Result<Long> = withContext(ioDispatcher) {
        runCatching {
            val id = userDao.insert(user.toEntity())   // syncPending = 1 (default)
            RemoteSyncWorker.enqueueAfterWrite(context)
            id
        }
    }

    override suspend fun update(user: User): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            userDao.update(user.toEntity())             // syncPending = 1 (default)
            RemoteSyncWorker.enqueueAfterWrite(context)
        }
    }

    override suspend fun setActive(id: Long, isActive: Boolean): Unit = withContext(ioDispatcher) {
        userDao.setActive(id, isActive)                 // SQL: syncPending = 1 ya incluido
        RemoteSyncWorker.enqueueAfterWrite(context)
    }

    override suspend fun delete(id: Long): Unit = withContext(ioDispatcher) {
        val nick = userDao.findById(id)?.nick
        userDao.delete(id)
        if (nick != null) {
            syncScope.launch {
                remoteDataSource.deleteRow("users", nick)
                    .onFailure { Log.w("UsersRepo", "deleteRow Sheets falló", it) }
            }
        }
        Unit
    }
}
