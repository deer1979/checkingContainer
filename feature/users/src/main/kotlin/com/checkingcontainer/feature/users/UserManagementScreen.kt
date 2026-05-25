package com.checkingcontainer.feature.users

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.checkingcontainer.core.model.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementRoute(
    onAddUser: () -> Unit,
    onEditUser: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: UsersListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Usuarios") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Ajustes")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddUser) {
                Icon(Icons.Outlined.Add, contentDescription = "Crear usuario")
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (state.users.isEmpty() && !state.isLoading) {
                EmptyUsers()
            } else {
                UsersList(
                    users = state.users,
                    onToggleActive = viewModel::onToggleActive,
                    onEdit = onEditUser,
                )
            }
        }
    }
}

@Composable
private fun UsersList(
    users: List<User>,
    onToggleActive: (Long, Boolean) -> Unit,
    onEdit: (Long) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(users, key = { it.id }) { user ->
            ListItem(
                headlineContent = { Text(user.fullName) },
                supportingContent = {
                    Text("${user.jobTitle.display} • ${user.company} • ${user.location}")
                },
                trailingContent = {
                    Switch(
                        checked = user.isActive,
                        onCheckedChange = { onToggleActive(user.id, it) },
                    )
                },
                modifier = Modifier.clickable { onEdit(user.id) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun EmptyUsers() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.PersonOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Aun no hay usuarios. Toca + para crear el primero.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}