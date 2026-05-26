package com.checkingcontainer.feature.announcements

import androidx.compose.runtime.Immutable
import com.checkingcontainer.core.model.Announcement
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class AnnouncementsUiState(
    val items: ImmutableList<Announcement> = persistentListOf(),
    val isLoading: Boolean = true,
)
