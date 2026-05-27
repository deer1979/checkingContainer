package com.checkingcontainer.core.data

import com.checkingcontainer.core.common.di.AppDispatcher
import com.checkingcontainer.core.common.di.Dispatcher
import com.checkingcontainer.core.database.dao.UserDao
import com.checkingcontainer.core.database.entity.UserEntity
import com.checkingcontainer.core.database.entity.toEntity
import com.checkingcontainer.core.domain.UsersRepository
import com.checkingcontainer.core.model.User
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repositorio de usuarios.
 *
 * Estado actual: **local-only** — Room es la única fuente de verdad.
 *
 * TODO: Inyectar [com.checkingcontainer.core.network.RemoteDataSource]
 *   (Google Sheets) y sincronizar con una hoja "Usuarios" via Sheets v4 API.
 */
@Singleton
class UsersRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : UsersRepository {

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
            val id = userDao.insert(user.toEntity())
            // TODO: pushCreate — Google Sheets API
            id
        }
    }

    override suspend fun update(user: User): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            userDao.update(user.toEntity())
            // TODO: pushUpdate — Google Sheets API
            Unit
        }
    }

    override suspend fun setActive(id: Long, isActive: Boolean): Unit = withContext(ioDispatcher) {
        userDao.setActive(id, isActive)
        // TODO: pushSetActive — Google Sheets API
    }

    override suspend fun delete(id: Long): Unit = withContext(ioDispatcher) {
        userDao.delete(id)
        // TODO: pushDelete — Google Sheets API
    }
}
