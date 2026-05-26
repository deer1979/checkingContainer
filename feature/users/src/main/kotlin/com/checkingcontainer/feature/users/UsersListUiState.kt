package com.checkingcontainer.feature.users

import androidx.compose.runtime.Immutable
import com.checkingcontainer.core.model.User
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class UsersListUiState(
    val users: ImmutableList<User> = persistentListOf(),
    val isLoading: Boolean = true,
)
