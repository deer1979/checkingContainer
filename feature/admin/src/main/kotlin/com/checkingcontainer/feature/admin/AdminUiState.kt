package com.checkingcontainer.feature.admin

data class AdminUiState(
    val draftTitle: String = "",
    val draftSummary: String = "",
    val draftBody: String = "",
    val publishedCount: Int = 0,
    val isPublishing: Boolean = false,
) {
    val canPublish: Boolean
        get() = !isPublishing && draftTitle.isNotBlank() && draftBody.isNotBlank()
}
