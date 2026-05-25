package com.checkingcontainer.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkingcontainer.core.domain.usecase.AddTaskUseCase
import com.checkingcontainer.core.domain.usecase.DeleteTaskUseCase
import com.checkingcontainer.core.domain.usecase.ObserveTasksUseCase
import com.checkingcontainer.core.domain.usecase.ToggleTaskUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class TasksViewModel @Inject constructor(
    observeTasks: ObserveTasksUseCase,
    private val addTask: AddTaskUseCase,
    private val toggleTask: ToggleTaskUseCase,
    private val deleteTask: DeleteTaskUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(TasksUiState())
    val state: StateFlow<TasksUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            observeTasks().collect { tasks ->
                _state.update { it.copy(tasks = tasks, isLoading = false) }
            }
        }
    }

    fun onDraftChange(value: String) {
        _state.update { it.copy(draftTitle = value) }
    }

    fun onAddClicked() {
        val title = _state.value.draftTitle
        if (title.isBlank()) return
        viewModelScope.launch {
            addTask(title)
            _state.update { it.copy(draftTitle = "") }
        }
    }

    fun onToggle(id: Long, done: Boolean) {
        viewModelScope.launch { toggleTask(id, done) }
    }

    fun onDelete(id: Long) {
        viewModelScope.launch { deleteTask(id) }
    }
}
