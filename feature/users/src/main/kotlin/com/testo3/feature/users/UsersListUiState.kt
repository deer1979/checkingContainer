package com.testo3.feature.users

import com.testo3.core.model.User

data class UsersListUiState(
    val users: List<User> = emptyList(),
    val isLoading: Boolean = true,
)
