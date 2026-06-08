package com.checkingcontainer.feature.admin

import android.net.Uri
import androidx.compose.runtime.Immutable

/** Adjunto elegido por el usuario, aún no subido (se sube al publicar). */
@Immutable
data class PendingAttachmentUi(
    val uri: Uri,
    val name: String,
    val contentType: String,
    val sizeBytes: Long,
) {
    val isImage: Boolean get() = contentType.startsWith("image/")
}

@Immutable
data class AdminUiState(
    val draftTitle: String = "",
    val draftSummary: String = "",
    val draftBody: String = "",
    val pendingAttachments: List<PendingAttachmentUi> = emptyList(),
    val isPublishing: Boolean = false,
    val published: Boolean = false,
    val errorMessage: String? = null,
) {
    val canPublish: Boolean
        get() = !isPublishing && draftTitle.isNotBlank() && draftBody.isNotBlank()
}
