package com.checkingcontainer.feature.users

import com.checkingcontainer.core.model.User

data class UsersListUiState(
    val users: List<User> = emptyList(),
    val isLoading: Boolean = true,
)
