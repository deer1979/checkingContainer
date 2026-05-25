package com.checkingcontainer.feature.announcements

import com.checkingcontainer.core.model.Announcement

data class AnnouncementsUiState(
    val items: List<Announcement> = emptyList(),
    val isLoading: Boolean = true,
)
