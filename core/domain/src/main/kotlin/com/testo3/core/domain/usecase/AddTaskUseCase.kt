package com.testo3.core.domain.usecase

import com.testo3.core.domain.TaskRepository
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
