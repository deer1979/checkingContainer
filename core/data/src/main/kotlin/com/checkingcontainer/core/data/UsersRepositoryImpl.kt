package com.checkingcontainer.core.data

import com.checkingcontainer.core.common.di.AppDispatcher
import com.checkingcontainer.core.common.di.Dispatcher
import com.checkingcontainer.core.data.remote.SupabaseSyncService
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

@Singleton
class UsersRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val syncService: SupabaseSyncService,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : UsersRepository {

    override fun observeAll(): Flow<List<User>> =
        userDao.observeAll()
            .map { rows -> rows.map(UserEntity::toDomain) }
            .flowOn(ioDispatcher)

    override suspend fun getById(id: Long): User? = withContext(ioDispatcher) {
        userDao.findById(id)?.toDomain()
    }

    override suspend fun create(user: User): Result<Long> = withContext(ioDispatcher) {
        runCatching {
            val entity = user.toEntity()
            val id = userDao.insert(entity)
            syncService.pushUser(entity.copy(id = id))
            id
        }
    }

    override suspend fun update(user: User): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val existing = userDao.findById(user.id)
            val entity = user.toEntity().copy(
                syncId = existing?.syncId ?: java.util.UUID.randomUUID().toString(),
                syncPending = true,
            )
            userDao.update(entity)
            syncService.pushUser(entity)
        }
    }

    override suspend fun setActive(id: Long, isActive: Boolean) = withContext(ioDispatcher) {
        userDao.setActive(id, isActive)
    }

    override suspend fun delete(id: Long) = withContext(ioDispatcher) {
        userDao.delete(id)
    }

    override suspend fun count(): Int = withContext(ioDispatcher) { userDao.count() }
}
