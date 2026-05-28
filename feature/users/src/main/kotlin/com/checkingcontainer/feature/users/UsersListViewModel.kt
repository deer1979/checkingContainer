package com.checkingcontainer.feature.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkingcontainer.core.domain.UsersRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class UsersListViewModel @Inject constructor(
    private val repository: UsersRepository,
) : ViewModel() {

    val state: StateFlow<UsersListUiState> = repository.observeAll()
        .map { UsersListUiState(users = it.toImmutableList(), isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UsersListUiState(),
        )

    fun onToggleActive(id: Long, isActive: Boolean) {
        viewModelScope.launch { repository.setActive(id, isActive) }
    }

    fun onDelete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}
