package com.checkingcontainer.feature.tasks

import com.checkingcontainer.core.model.Task

data class TasksUiState(
    val tasks: List<Task> = emptyList(),
    val draftTitle: String = "",
    val isLoading: Boolean = true,
)
