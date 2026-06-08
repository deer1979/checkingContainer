package com.checkingcontainer.feature.admin

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkingcontainer.core.domain.AnnouncementsRepository
import com.checkingcontainer.core.domain.AuthRepository
import com.checkingcontainer.core.domain.AuthState
import com.checkingcontainer.core.domain.PendingAttachment
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class AdminViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val announcements: AnnouncementsRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    fun onTitleChange(value: String) = _state.update { it.copy(draftTitle = value) }
    fun onSummaryChange(value: String) = _state.update { it.copy(draftSummary = value) }
    fun onBodyChange(value: String) = _state.update { it.copy(draftBody = value) }

    fun onAttachmentPicked(uri: Uri) {
        val picked = resolveAttachment(uri) ?: return
        _state.update { s ->
            if (s.pendingAttachments.any { it.uri == picked.uri }) s
            else s.copy(pendingAttachments = s.pendingAttachments + picked)
        }
    }

    fun onRemoveAttachment(uri: Uri) {
        _state.update { it.copy(pendingAttachments = it.pendingAttachments.filterNot { a -> a.uri == uri }) }
    }

    fun onPublish() {
        val current = _state.value
        if (!current.canPublish) return
        viewModelScope.launch {
            _state.update { it.copy(isPublishing = true, errorMessage = null) }
            val author = (authRepository.state.first() as? AuthState.Authenticated)
                ?.user?.fullName ?: "Admin"
            val result = runCatching {
                val pending = withContext(Dispatchers.IO) {
                    current.pendingAttachments.mapNotNull { it.toPending() }
                }
                announcements.publish(
                    title = current.draftTitle,
                    summary = current.draftSummary.ifBlank { current.draftTitle },
                    body = current.draftBody,
                    authorName = author,
                    attachments = pending,
                )
            }
            if (result.isSuccess) {
                _state.update { it.copy(isPublishing = false, published = true) }
            } else {
                _state.update {
                    it.copy(
                        isPublishing = false,
                        errorMessage = "No se pudo publicar. Revisa tu conexión e inténtalo de nuevo.",
                    )
                }
            }
        }
    }

    /** Lee nombre, tipo y tamaño del Uri elegido (sin leer aún los bytes). */
    private fun resolveAttachment(uri: Uri): PendingAttachmentUi? = runCatching {
        val resolver = context.contentResolver
        val contentType = resolver.getType(uri) ?: "application/octet-stream"
        var name = "archivo"
        var size = 0L
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) cursor.getString(nameIdx)?.let { name = it }
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
            }
        }
        PendingAttachmentUi(uri = uri, name = name, contentType = contentType, sizeBytes = size)
    }.getOrNull()

    /** Lee los bytes del Uri para subirlo. Debe llamarse en un hilo de IO. */
    private fun PendingAttachmentUi.toPending(): PendingAttachment? = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return null
        PendingAttachment(name = name, contentType = contentType, bytes = bytes, sizeBytes = sizeBytes)
    }.getOrNull()
}
