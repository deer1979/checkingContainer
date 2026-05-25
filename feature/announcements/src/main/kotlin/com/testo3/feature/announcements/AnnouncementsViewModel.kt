package com.testo3.feature.announcements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.testo3.core.domain.AnnouncementsRepository
import com.testo3.core.model.Announcement
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AnnouncementsViewModel @Inject constructor(
    private val repository: AnnouncementsRepository,
) : ViewModel() {

    val list: StateFlow<AnnouncementsUiState> = repository.observeAll()
        .map { items -> AnnouncementsUiState(items = items, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AnnouncementsUiState(),
        )

    private val _detail = MutableStateFlow<Announcement?>(null)
    val detail: StateFlow<Announcement?> = _detail.asStateFlow()

    fun loadDetail(id: String) {
        viewModelScope.launch {
            _detail.update { repository.getById(id) }
        }
    }
}
