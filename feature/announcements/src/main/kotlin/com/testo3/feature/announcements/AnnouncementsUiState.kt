package com.testo3.feature.announcements

import com.testo3.core.model.Announcement

data class AnnouncementsUiState(
    val items: List<Announcement> = emptyList(),
    val isLoading: Boolean = true,
)
