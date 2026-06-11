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
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    viewModel: UsersListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Usuarios") },
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
                    onDelete = viewModel::onDelete,
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
    onDelete: (Long) -> Unit,
) {
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(users, key = { it.id }, contentType = { "user" }) { user ->
            var menuExpanded by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text(user.fullName) },
                supportingContent = {
                    Text("${user.jobTitle.display} • ${user.company} • ${user.location}")
                },
                trailingContent = {
                    Box {
                        Switch(
                            checked = user.isActive,
                            onCheckedChange = { onToggleActive(user.id, it) },
                        )
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = "Opciones",
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Editar") },
                                onClick = {
                                    menuExpanded = false
                                    onEdit(user.id)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Eliminar") },
                                onClick = {
                                    menuExpanded = false
                                    pendingDeleteId = user.id
                                },
                            )
                        }
                    }
                },
                modifier = Modifier.clickable { onEdit(user.id) },
            )
            HorizontalDivider()
        }
    }

    if (pendingDeleteId != null) {
        DeleteConfirmationDialog(
            title = "¿Eliminar usuario?",
            text = "Esta acción no tiene vuelta atrás. El registro se eliminará también de Google Sheets.",
            onConfirm = {
                onDelete(pendingDeleteId!!)
                pendingDeleteId = null
            },
            onDismiss = { pendingDeleteId = null },
        )
    }
}

@Composable
internal fun DeleteConfirmationDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "Eliminar",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
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
