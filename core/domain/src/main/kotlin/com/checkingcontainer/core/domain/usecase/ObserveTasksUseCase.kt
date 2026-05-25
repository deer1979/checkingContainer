package com.checkingcontainer.core.domain.usecase

import com.checkingcontainer.core.domain.TaskRepository
import com.checkingcontainer.core.model.Task
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveTasksUseCase @Inject constructor(
    private val repository: TaskRepository,
) {
    operator fun invoke(): Flow<List<Task>> = repository.observeTasks()
}
