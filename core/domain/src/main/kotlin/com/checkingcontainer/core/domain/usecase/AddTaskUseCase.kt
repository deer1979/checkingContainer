package com.checkingcontainer.core.domain.usecase

import com.checkingcontainer.core.domain.TaskRepository
import javax.inject.Inject

class AddTaskUseCase @Inject constructor(
    private val repository: TaskRepository,
) {
    suspend operator fun invoke(title: String) {
        val trimmed = title.trim()
        require(trimmed.isNotEmpty()) { "Task title must not be blank" }
        repository.addTask(trimmed)
    }
}
