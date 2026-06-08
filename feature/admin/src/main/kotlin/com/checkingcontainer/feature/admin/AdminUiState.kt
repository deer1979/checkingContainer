package com.checkingcontainer.feature.admin

import androidx.compose.runtime.Immutable

@Immutable
data class AdminUiState(
    val draftTitle: String = "",
    val draftSummary: String = "",
    val draftBody: String = "",
    val isPublishing: Boolean = false,
    val published: Boolean = false,
) {
    val canPublish: Boolean
        get() = !isPublishing && draftTitle.isNotBlank() && draftBody.isNotBlank()
}
