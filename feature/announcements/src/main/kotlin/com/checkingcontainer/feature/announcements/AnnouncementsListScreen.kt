package com.checkingcontainer.feature.announcements

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.checkingcontainer.core.designsystem.UserAvatarMenu
import com.checkingcontainer.core.model.User

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementsListRoute(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onAnnouncementClick: (String) -> Unit,
    isAdmin: Boolean = false,
    onCreateClick: () -> Unit = {},
    user: User? = null,
    onSettingsClick: () -> Unit = {},
    onLogout: () -> Unit = {},
    viewModel: AnnouncementsViewModel = hiltViewModel(),
) {
    val state by viewModel.list.collectAsStateWithLifecycle()
    // Al entrar (y cada vez que llega un anuncio nuevo mientras se ve la lista),
    // marcar todo como leído para que el badge de la pestaña quede en cero.
    LaunchedEffect(state.items) { viewModel.markAllSeen() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Anuncios") },
                actions = {
                    if (isAdmin) {
                        IconButton(onClick = onCreateClick) {
                            Icon(Icons.Outlined.Add, contentDescription = "Crear anuncio")
                        }
                    }
                    if (user != null) {
                        UserAvatarMenu(
                            user = user,
                            onSettingsClick = onSettingsClick,
                            onLogout = onLogout,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (!state.isLoading && state.items.isEmpty()) {
            EmptyAnnouncements(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        } else {
            AnnouncementsList(
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                state = state,
                onAnnouncementClick = onAnnouncementClick,
                onDelete = viewModel::onDelete,
                contentPadding = PaddingValues(
                    start = innerPadding.calculateStartPadding(LayoutDirection.Ltr) + 16.dp,
                    end = innerPadding.calculateEndPadding(LayoutDirection.Ltr) + 16.dp,
                    top = innerPadding.calculateTopPadding() + 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp,
                ),
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AnnouncementsList(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    state: AnnouncementsUiState,
    onAnnouncementClick: (String) -> Unit,
    onDelete: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(16.dp),
) {
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        items(state.items, key = { it.id }) { item ->
            AnnouncementCard(
                announcement = item,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                onClick = { onAnnouncementClick(item.id) },
                onDeleteRequest = { pendingDeleteId = item.id },
            )
            Spacer(Modifier.height(12.dp))
        }
    }

    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("¿Eliminar anuncio?") },
            text = { Text("Esta acción no puede deshacerse. El registro se eliminará también de la nube.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(pendingDeleteId!!)
                        pendingDeleteId = null
                    },
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Cancelar") }
            },
        )
    }
}
