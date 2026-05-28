package com.checkingcontainer.feature.announcements

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.checkingcontainer.core.model.Announcement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementsListRoute(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onAnnouncementClick: (String) -> Unit,
    viewModel: AnnouncementsViewModel = hiltViewModel(),
) {
    val state by viewModel.list.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Anuncios") })
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
            text = { Text("Esta acción no tiene vuelta atrás. El registro se eliminará también de Google Sheets.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(pendingDeleteId!!)
                        pendingDeleteId = null
                    },
                ) {
                    Text(
                        text = "Eliminar",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AnnouncementCard(
    announcement: Announcement,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    with(sharedTransitionScope) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Card(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .sharedBounds(
                        sharedContentState = rememberSharedContentState("announcement-card-${announcement.id}"),
                        animatedVisibilityScope = animatedVisibilityScope,
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = announcement.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.sharedElement(
                            sharedContentState = rememberSharedContentState("announcement-title-${announcement.id}"),
                            animatedVisibilityScope = animatedVisibilityScope,
                        ),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = announcement.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = formatDate(announcement.publishedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "Opciones",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Eliminar") },
                        onClick = {
                            menuExpanded = false
                            onDeleteRequest()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyAnnouncements(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Campaign,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "No hay anuncios publicados todavía.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()).format(Date(millis))
