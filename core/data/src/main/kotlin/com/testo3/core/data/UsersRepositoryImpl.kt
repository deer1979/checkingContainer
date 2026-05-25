package com.testo3.core.data

import com.testo3.core.common.di.AppDispatcher
import com.testo3.core.common.di.Dispatcher
import com.testo3.core.database.dao.UserDao
import com.testo3.core.database.entity.UserEntity
import com.testo3.core.database.entity.toEntity
import com.testo3.core.domain.UsersRepository
import com.testo3.core.model.User
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
        runCatching { userDao.insert(user.toEntity()) }
    }

    override suspend fun update(user: User): Result<Unit> = withContext(ioDispatcher) {
        runCatching { userDao.update(user.toEntity()) }
    }

    override suspend fun setActive(id: Long, isActive: Boolean) = withContext(ioDispatcher) {
        userDao.setActive(id, isActive)
    }

    override suspend fun delete(id: Long) = withContext(ioDispatcher) {
        userDao.delete(id)
    }

    override suspend fun count(): Int = withContext(ioDispatcher) { userDao.count() }
}
