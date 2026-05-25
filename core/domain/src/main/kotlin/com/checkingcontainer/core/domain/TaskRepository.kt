package com.checkingcontainer.core.domain

import com.checkingcontainer.core.model.Task
import kotlinx.coroutines.flow.Flow

/**
 * Contract that the data layer fulfils. Domain code (use cases) and feature
 * code (ViewModels) depend on this interface, never on Room/DAO classes.
 * Swap to a remote/cloud implementation later by providing a different
 * Repository binding in DI — no change anywhere else.
 */
interface TaskRepository {
    fun observeTasks(): Flow<List<Task>>
    suspend fun addTask(title: String)
    suspend fun setDone(id: Long, done: Boolean)
    suspend fun delete(id: Long)
}
