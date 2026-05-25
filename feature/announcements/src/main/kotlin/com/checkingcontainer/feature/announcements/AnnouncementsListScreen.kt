package com.checkingcontainer.feature.announcements

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.checkingcontainer.core.model.Announcement

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AnnouncementsListRoute(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onAnnouncementClick: (String) -> Unit,
    viewModel: AnnouncementsViewModel = hiltViewModel(),
) {
    val state by viewModel.list.collectAsStateWithLifecycle()
    AnnouncementsList(
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        state = state,
        onAnnouncementClick = onAnnouncementClick,
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AnnouncementsList(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    state: AnnouncementsUiState,
    onAnnouncementClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        items(state.items, key = { it.id }) { item ->
            AnnouncementCard(
                announcement = item,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                onClick = { onAnnouncementClick(item.id) },
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AnnouncementCard(
    announcement: Announcement,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
) {
    with(sharedTransitionScope) {
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
            }
        }
    }
}
