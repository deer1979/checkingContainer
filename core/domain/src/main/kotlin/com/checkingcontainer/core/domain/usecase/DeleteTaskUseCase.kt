package com.checkingcontainer.core.domain.usecase

import com.checkingcontainer.core.domain.TaskRepository
import javax.inject.Inject

class DeleteTaskUseCase @Inject constructor(
    private val repository: TaskRepository,
) {
    suspend operator fun invoke(id: Long) {
        repository.delete(id)
    }
}
