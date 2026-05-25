package com.testo3.feature.tasks

import com.testo3.core.model.Task

data class TasksUiState(
    val tasks: List<Task> = emptyList(),
    val draftTitle: String = "",
    val isLoading: Boolean = true,
)
