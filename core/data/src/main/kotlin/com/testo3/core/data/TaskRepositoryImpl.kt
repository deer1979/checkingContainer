package com.testo3.core.data

import com.testo3.core.common.di.AppDispatcher
import com.testo3.core.common.di.Dispatcher
import com.testo3.core.database.dao.TaskDao
import com.testo3.core.database.entity.TaskEntity
import com.testo3.core.domain.TaskRepository
import com.testo3.core.model.Task
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Single Repository implementation backed by Room. To swap to a cloud
 * source later, provide a different binding for [TaskRepository] — the
 * domain and feature layers don't need to change.
 */
@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : TaskRepository {

    override fun observeTasks(): Flow<List<Task>> =
        taskDao.observe()
            .map { entities -> entities.map(TaskEntity::toDomain) }
            .flowOn(ioDispatcher)

    override suspend fun addTask(title: String) = withContext(ioDispatcher) {
        taskDao.insert(
            TaskEntity(
                title = title,
                isDone = false,
                createdAt = System.currentTimeMillis(),
            )
        )
        Unit
    }

    override suspend fun setDone(id: Long, done: Boolean) = withContext(ioDispatcher) {
        taskDao.setDone(id, done)
    }

    override suspend fun delete(id: Long) = withContext(ioDispatcher) {
        taskDao.delete(id)
    }
}
