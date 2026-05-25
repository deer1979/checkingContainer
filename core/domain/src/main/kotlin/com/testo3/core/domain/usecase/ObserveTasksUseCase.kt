package com.testo3.core.domain.usecase

import com.testo3.core.domain.TaskRepository
import com.testo3.core.model.Task
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveTasksUseCase @Inject constructor(
    private val repository: TaskRepository,
) {
    operator fun invoke(): Flow<List<Task>> = repository.observeTasks()
}
