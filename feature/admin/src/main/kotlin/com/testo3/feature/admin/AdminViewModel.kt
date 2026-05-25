package com.testo3.feature.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.testo3.core.domain.AnnouncementsRepository
import com.testo3.core.domain.AuthRepository
import com.testo3.core.domain.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val announcements: AnnouncementsRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    fun onTitleChange(value: String) = _state.update { it.copy(draftTitle = value) }
    fun onSummaryChange(value: String) = _state.update { it.copy(draftSummary = value) }
    fun onBodyChange(value: String) = _state.update { it.copy(draftBody = value) }

    fun onPublish() {
        val current = _state.value
        if (!current.canPublish) return
        viewModelScope.launch {
            _state.update { it.copy(isPublishing = true) }
            val author = (authRepository.state.first() as? AuthState.Authenticated)
                ?.user?.fullName ?: "Admin"
            announcements.publish(
                title = current.draftTitle,
                summary = current.draftSummary.ifBlank { current.draftTitle },
                body = current.draftBody,
                authorName = author,
            )
            _state.update {
                AdminUiState(publishedCount = it.publishedCount + 1)
            }
        }
    }
}
