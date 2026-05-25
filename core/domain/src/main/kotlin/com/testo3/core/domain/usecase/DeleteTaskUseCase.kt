package com.testo3.core.domain.usecase

import com.testo3.core.domain.TaskRepository
import javax.inject.Inject

class DeleteTaskUseCase @Inject constructor(
    private val repository: TaskRepository,
) {
    suspend operator fun invoke(id: Long) {
        repository.delete(id)
    }
}
